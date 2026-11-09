package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import pl.pz.supplyblockchain.application.ports.StationRegistryException;
import pl.pz.supplyblockchain.application.ports.StationRegistryException.Reason;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StationRegistration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code getStation} over a stub JSON-RPC server. Golden values come from Foundry 1.8.6:
 * {@code cast calldata "getStation(address)" <station>} and
 * {@code cast abi-encode "f(bool,bytes32,uint64,uint32)" ...}.
 */
class Web3jStationRegistryTest {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String STATION_CHECKSUM = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
    private static final String STATION_ID = "0x071dc5a525d6ea42c4b169703ea5c1776b953512942daac912791c213858f46c";
    private static final String GET_STATION_CALL =
            "0xadee2c6200000000000000000000000070997970c51812dc3a010c7d01b50e0d17dc79c8";
    /** true, ST-001, 1831536000, 0x0F. */
    private static final String ACTIVE = "0x"
            + "0000000000000000000000000000000000000000000000000000000000000001"
            + "071dc5a525d6ea42c4b169703ea5c1776b953512942daac912791c213858f46c"
            + "000000000000000000000000000000000000000000000000000000006d2b0580"
            + "000000000000000000000000000000000000000000000000000000000000000f";
    /** After {@code revokeStation}: false, ST-001, 1831536000, 0x0F. */
    private static final String REVOKED = "0x"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "071dc5a525d6ea42c4b169703ea5c1776b953512942daac912791c213858f46c"
            + "000000000000000000000000000000000000000000000000000000006d2b0580"
            + "000000000000000000000000000000000000000000000000000000000000000f";
    /** true, ST-001, type(uint64).max, 0x03. */
    private static final String MAX_UINT64 = "0x"
            + "0000000000000000000000000000000000000000000000000000000000000001"
            + "071dc5a525d6ea42c4b169703ea5c1776b953512942daac912791c213858f46c"
            + "000000000000000000000000000000000000000000000000ffffffffffffffff"
            + "0000000000000000000000000000000000000000000000000000000000000003";
    private static final String NEVER_REGISTERED = "0x" + "0".repeat(256);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private volatile int httpStatus;
    private volatile String responseBody;
    private HttpServer server;
    private Web3j web3j;

    @BeforeEach
    void startStubRpc() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                JsonNode request = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
                requests.add(request);
                byte[] body = responseBody.replace("$ID", request.get("id").toString())
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(httpStatus, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        web3j = Web3j.build(new HttpService("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stopStubRpc() {
        web3j.shutdown();
        server.stop(0);
    }

    @Test
    void readsRegistrationWithEthCallToTheDeploymentAtLatestBlock() {
        respondWithResult(ACTIVE);

        StationRegistration registration = registry().getStation(STATION_CHECKSUM);

        assertThat(registration).isEqualTo(new StationRegistration(true, STATION_ID, 1_831_536_000L, 0x0F));
        assertThat(requests).hasSize(1);
        JsonNode request = requests.getFirst();
        assertThat(request.get("method").asString()).isEqualTo("eth_call");
        assertThat(request.at("/params/0/to").asString()).isEqualTo(CONTRACT);
        assertThat(request.at("/params/0/data").asString()).isEqualTo(GET_STATION_CALL);
        assertThat(request.at("/params/1").asString()).isEqualTo("latest");
    }

    @Test
    void revokedStationKeepsItsStationId() {
        respondWithResult(REVOKED);

        StationRegistration registration = registry().getStation(STATION);

        assertThat(registration.active()).isFalse();
        assertThat(registration.everRegistered()).isTrue();
        assertThat(registration.stationId()).isEqualTo(STATION_ID);
    }

    @Test
    void unknownAddressReadsAsNeverRegistered() {
        respondWithResult(NEVER_REGISTERED);

        StationRegistration registration = registry().getStation(STATION);

        assertThat(registration.active()).isFalse();
        assertThat(registration.everRegistered()).isFalse();
        assertThat(registration.stationId()).isEqualTo(StationRegistration.ZERO_STATION_ID);
    }

    @Test
    void expiresAtAboveTheJavaLongRangeSaturates() {
        StationRegistration registration = Web3jStationRegistry.decodeGetStation(MAX_UINT64);

        assertThat(registration.expiresAt()).isEqualTo(Long.MAX_VALUE);
        assertThat(registration.stageMask()).isEqualTo(0x03);
    }

    @Test
    void jsonRpcErrorIsARetryableAvailabilityError() {
        httpStatus = 200;
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"error\":{\"code\":-32000,\"message\":\"header not found\"}}";

        assertFailure(Reason.UNAVAILABLE, true);
    }

    @Test
    void httpErrorIsARetryableAvailabilityError() {
        httpStatus = 503;
        responseBody = "busy";

        assertFailure(Reason.UNAVAILABLE, true);
    }

    @Test
    void unreachableRpcIsARetryableAvailabilityError() {
        server.stop(0);

        assertFailure(Reason.UNAVAILABLE, true);
    }

    static Stream<Arguments> wrongSizeResults() {
        return Stream.of(
                Arguments.of("empty (no contract at address)", "0x"),
                Arguments.of("one byte", "0x00"),
                Arguments.of("no 0x prefix", "short"),
                Arguments.of("127 bytes", "0x" + "00".repeat(127)),
                Arguments.of("129 bytes", "0x" + "00".repeat(129)));
    }

    /** {@code eth_call} to an address without code returns {@code 0x}: wrong CONTRACT_ADDRESS, not "unregistered". */
    @ParameterizedTest(name = "{0}")
    @MethodSource("wrongSizeResults")
    void resultOfTheWrongSizeIsAnInvalidResponse(String name, String result) {
        respondWithResult(result);

        assertFailure(Reason.INVALID_RESPONSE, false);
    }

    static Stream<Arguments> resultsOutsideTheAbiTypes() {
        return Stream.of(
                Arguments.of("bool = 2", "0x" + "0".repeat(63) + "2" + ACTIVE.substring(66)),
                Arguments.of("dirty padding above uint64 expiresAt",
                        ACTIVE.substring(0, 130) + "01" + ACTIVE.substring(132)),
                Arguments.of("dirty padding above uint32 stageMask",
                        ACTIVE.substring(0, 194) + "01" + ACTIVE.substring(196)),
                Arguments.of("non-hex", "0x" + "zz".repeat(128)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resultsOutsideTheAbiTypes")
    void resultOutsideTheAbiTypesIsAnInvalidResponse(String name, String result) {
        respondWithResult(result);

        assertFailure(Reason.INVALID_RESPONSE, false);
    }

    @Test
    void encodesTheSameCalldataAsCast() {
        assertThat(Web3jStationRegistry.encodeGetStation(STATION_CHECKSUM)).isEqualTo(GET_STATION_CALL);
        assertThatThrownBy(() -> Web3jStationRegistry.encodeGetStation("0x1234"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Web3jStationRegistry registry() {
        return new Web3jStationRegistry(web3j, new DeploymentId(31337, CONTRACT));
    }

    private void respondWithResult(String result) {
        httpStatus = 200;
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":\"" + result + "\"}";
    }

    private void assertFailure(Reason reason, boolean retryable) {
        assertThatThrownBy(() -> registry().getStation(STATION))
                .isInstanceOfSatisfying(StationRegistryException.class, error -> {
                    assertThat(error.reason()).isEqualTo(reason);
                    assertThat(error.reason().retryable()).isEqualTo(retryable);
                });
    }
}
