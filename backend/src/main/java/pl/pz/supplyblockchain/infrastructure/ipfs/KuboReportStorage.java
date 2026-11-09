package pl.pz.supplyblockchain.infrastructure.ipfs;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pl.pz.supplyblockchain.application.ports.ReportStorage;
import pl.pz.supplyblockchain.application.ports.ReportStorageException;
import pl.pz.supplyblockchain.application.ports.ReportStorageException.Reason;
import pl.pz.supplyblockchain.application.ports.StoredReport;
import pl.pz.supplyblockchain.config.KuboProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Stores reports in a Kubo node through its RPC API. The JCS bytes go up as a file with {@code cid-version=1},
 * so the CID depends only on the bytes, and are read back with {@code cat} to check that Kubo keeps exactly
 * what we sent.
 */
@Component
public class KuboReportStorage implements ReportStorage {

    private static final String PROVIDER = "kubo";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxReportBytes;

    @Autowired
    public KuboReportStorage(KuboProperties properties, ObjectMapper objectMapper) {
        this(buildClient(properties), objectMapper, properties.maxReportBytes());
    }

    KuboReportStorage(RestClient restClient, ObjectMapper objectMapper, int maxReportBytes) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.maxReportBytes = maxReportBytes;
    }

    @Override
    public StoredReport store(byte[] canonicalReportJcs) {
        if (canonicalReportJcs == null || canonicalReportJcs.length == 0) {
            throw new ReportStorageException(Reason.REJECTED, "report bytes are empty", null);
        }
        if (canonicalReportJcs.length > maxReportBytes) {
            throw new ReportStorageException(Reason.REJECTED,
                    "report has " + canonicalReportJcs.length + " bytes, limit is " + maxReportBytes, null);
        }
        String cid = add(canonicalReportJcs);
        byte[] stored = cat(cid, canonicalReportJcs.length);
        if (!Arrays.equals(stored, canonicalReportJcs)) {
            throw new ReportStorageException(Reason.INVALID_RESPONSE,
                    "bytes read back from Kubo for " + cid + " differ from the report", null);
        }
        return new StoredReport(cid, PROVIDER);
    }

    private String add(byte[] report) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(report))
                .filename("report.json")
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        String response = call("add", () -> restClient.post()
                .uri(uri -> uri.path("/api/v0/add")
                        .queryParam("cid-version", 1)
                        .queryParam("pin", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(String.class));
        String cid = parseHash(response);
        if (!CidValidator.isValid(cid)) {
            throw new ReportStorageException(Reason.INVALID_RESPONSE, "Kubo returned an invalid CID: " + cid, null);
        }
        return cid;
    }

    private byte[] cat(String cid, int expectedLength) {
        // one extra byte so that a longer stored file is noticed
        byte[] bytes = call("cat", () -> restClient.post()
                .uri(uri -> uri.path("/api/v0/cat")
                        .queryParam("arg", cid)
                        .queryParam("length", expectedLength + 1)
                        .build())
                .retrieve()
                .body(byte[].class));
        return bytes == null ? new byte[0] : bytes;
    }

    private String parseHash(String response) {
        if (response == null || response.isBlank()) {
            throw new ReportStorageException(Reason.INVALID_RESPONSE, "Kubo add returned an empty body", null);
        }
        JsonNode hash;
        try {
            hash = objectMapper.readTree(response).get("Hash");
        } catch (JacksonException exception) {
            throw new ReportStorageException(Reason.INVALID_RESPONSE, "Kubo add returned invalid JSON", exception);
        }
        if (hash == null || !hash.isString() || hash.asString().isBlank()) {
            throw new ReportStorageException(Reason.INVALID_RESPONSE, "Kubo add response has no Hash", null);
        }
        return hash.asString();
    }

    private static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            throw fromStatus(operation, exception.getStatusCode(), exception);
        } catch (ResourceAccessException exception) {
            throw fromIo(operation, exception);
        } catch (RestClientException exception) {
            throw new ReportStorageException(Reason.INVALID_RESPONSE,
                    "Kubo " + operation + " returned an unreadable response", exception);
        }
    }

    private static ReportStorageException fromStatus(String operation, HttpStatusCode status, Exception cause) {
        int code = status.value();
        Reason reason;
        if (code == 401 || code == 403) {
            reason = Reason.UNAUTHORIZED;
        } else if (code == 429) {
            reason = Reason.RATE_LIMITED;
        } else if (status.is5xxServerError()) {
            reason = Reason.UNAVAILABLE;
        } else {
            reason = Reason.REJECTED;
        }
        return new ReportStorageException(reason, "Kubo " + operation + " failed with HTTP " + code, cause);
    }

    private static ReportStorageException fromIo(String operation, ResourceAccessException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            // the request never reached Kubo
            if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException) {
                return new ReportStorageException(Reason.UNAVAILABLE, "Kubo is not reachable", exception);
            }
            // the request may have been processed, so this is reported as a timeout
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return new ReportStorageException(Reason.TIMEOUT, "Kubo " + operation + " timed out", exception);
            }
        }
        return new ReportStorageException(Reason.UNAVAILABLE, "Kubo " + operation + " failed: "
                + exception.getMessage(), exception);
    }

    private static RestClient buildClient(KuboProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.apiUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
