package pl.pz.supplyblockchain.infrastructure.ipfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;
import pl.pz.supplyblockchain.application.ports.ReportStorageException;
import pl.pz.supplyblockchain.application.ports.ReportStorageException.Reason;
import pl.pz.supplyblockchain.application.ports.StoredReport;
import pl.pz.supplyblockchain.config.KuboProperties;
import tools.jackson.databind.json.JsonMapper;

class KuboReportStorageTest {

    private static final String BASE_URL = "http://kubo.test";
    private static final String CID = "bafkreic6hfd5nlfch6sjutxn63tbjptj4x4pxo5eeocylmhb5gko2q4ngy";
    private static final byte[] REPORT = "{\"qaPassed\":true,\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8);

    private MockRestServiceServer server;
    private KuboReportStorage storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new KuboReportStorage(builder.build(), JsonMapper.builder().build(), 1024);
    }

    @Test
    void uploadsExactBytesAsFileAndChecksThemBack() {
        expectAdd()
                .andExpect(queryParam("cid-version", "1"))
                .andExpect(queryParam("pin", "true"))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request)
                            .getBodyAsString();
                    assertThat(body).contains("filename=\"report.json\"");
                    assertThat(body).contains(new String(REPORT, StandardCharsets.UTF_8));
                })
                .andRespond(withSuccess(addResponse(CID), MediaType.APPLICATION_JSON));
        expectCat().andExpect(queryParam("arg", CID))
                .andRespond(withSuccess(REPORT, MediaType.APPLICATION_OCTET_STREAM));

        StoredReport stored = storage.store(REPORT);

        assertThat(stored.cid()).isEqualTo(CID);
        assertThat(stored.provider()).isEqualTo("kubo");
        server.verify();
    }

    @Test
    void rejectsEmptyAndTooLargeReportsWithoutCallingKubo() {
        assertReason(() -> storage.store(new byte[0]), Reason.REJECTED);
        assertReason(() -> storage.store(null), Reason.REJECTED);
        assertReason(() -> storage.store(new byte[1025]), Reason.REJECTED);
        server.verify();
    }

    @Test
    void failsWhenKuboReturnsDifferentBytes() {
        expectAdd().andRespond(withSuccess(addResponse(CID), MediaType.APPLICATION_JSON));
        expectCat().andRespond(withSuccess("{\"qaPassed\":false}", MediaType.APPLICATION_OCTET_STREAM));

        assertReason(() -> storage.store(REPORT), Reason.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not json",
            "{\"Name\":\"report.json\",\"Size\":\"34\"}",
            "{\"Hash\":\"\"}",
            "{\"Hash\":123}",
            "{\"Hash\":\"QmNotARealCid\"}",
            "{\"Hash\":\"https://ipfs.io/ipfs/" + CID + "\"}"
    })
    void badAddResponseIsInvalid(String body) {
        expectAdd().andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertReason(() -> storage.store(REPORT), Reason.INVALID_RESPONSE);
    }

    static Stream<Arguments> statuses() {
        return Stream.of(
                Arguments.of(HttpStatus.UNAUTHORIZED, Reason.UNAUTHORIZED),
                Arguments.of(HttpStatus.FORBIDDEN, Reason.UNAUTHORIZED),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, Reason.RATE_LIMITED),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, Reason.UNAVAILABLE),
                Arguments.of(HttpStatus.BAD_GATEWAY, Reason.UNAVAILABLE),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, Reason.UNAVAILABLE),
                Arguments.of(HttpStatus.BAD_REQUEST, Reason.REJECTED),
                Arguments.of(HttpStatus.CONTENT_TOO_LARGE, Reason.REJECTED));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("statuses")
    void httpErrorsMapToReasons(HttpStatus status, Reason reason) {
        expectAdd().andRespond(withStatus(status));

        assertReason(() -> storage.store(REPORT), reason);
    }

    @Test
    void errorWhileReadingBackIsReportedToo() {
        expectAdd().andRespond(withSuccess(addResponse(CID), MediaType.APPLICATION_JSON));
        expectCat().andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertReason(() -> storage.store(REPORT), Reason.UNAVAILABLE);
    }

    static Stream<Arguments> ioErrors() {
        return Stream.of(
                Arguments.of(new SocketTimeoutException("Read timed out"), Reason.TIMEOUT),
                Arguments.of(new HttpTimeoutException("request timed out"), Reason.TIMEOUT),
                Arguments.of(new HttpConnectTimeoutException("connect timed out"), Reason.UNAVAILABLE),
                Arguments.of(new ConnectException("Connection refused"), Reason.UNAVAILABLE),
                Arguments.of(new IOException("connection reset"), Reason.UNAVAILABLE));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("ioErrors")
    void networkErrorsMapToReasons(IOException error, Reason reason) {
        expectAdd().andRespond(withException(error));

        assertReason(() -> storage.store(REPORT), reason);
    }

    // the two tests below use the real JDK HTTP client, not the mock server

    @Test
    void closedPortIsUnavailable() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        assertReason(() -> realStorage(port, Duration.ofSeconds(5)).store(REPORT), Reason.UNAVAILABLE);
    }

    @Test
    void silentServerIsTimeout() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            // accepts the connection but never answers
            assertReason(() -> realStorage(socket.getLocalPort(), Duration.ofMillis(300)).store(REPORT),
                    Reason.TIMEOUT);
        }
    }

    private ResponseActions expectAdd() {
        return server.expect(requestTo(startsWith(BASE_URL + "/api/v0/add")))
                .andExpect(method(HttpMethod.POST));
    }

    private ResponseActions expectCat() {
        return server.expect(requestTo(startsWith(BASE_URL + "/api/v0/cat")))
                .andExpect(method(HttpMethod.POST));
    }

    private static KuboReportStorage realStorage(int port, Duration readTimeout) {
        return new KuboReportStorage(
                new KuboProperties("http://127.0.0.1:" + port, Duration.ofSeconds(2), readTimeout, 1024),
                JsonMapper.builder().build());
    }

    private static String addResponse(String cid) {
        return "{\"Name\":\"report.json\",\"Hash\":\"" + cid + "\",\"Size\":\"34\"}";
    }

    private static void assertReason(ThrowingCallable call, Reason reason) {
        assertThatThrownBy(call)
                .isInstanceOf(ReportStorageException.class)
                .satisfies(exception -> assertThat(((ReportStorageException) exception).reason()).isEqualTo(reason));
    }
}
