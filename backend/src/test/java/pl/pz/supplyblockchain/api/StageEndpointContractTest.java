package pl.pz.supplyblockchain.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.pz.supplyblockchain.application.AuthService;
import pl.pz.supplyblockchain.application.SubmitStageService;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.StageCommand;
import pl.pz.supplyblockchain.support.OpenApiSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Checks that what POST /stages really returns is described by shared/schemas/openapi.yaml. */
@WebMvcTest(StageController.class)
class StageEndpointContractTest {

    private static final String PATH = "/api/v1/batches/{batchId}/stages";
    private static final OpenApiSpec SPEC = OpenApiSpec.load();
    private static final JsonNode OPERATION = SPEC.operation(PATH, "post");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitStageService submitStageService;

    @MockitoBean
    private AuthService authService;

    @Test
    void acceptedResponseMatchesSpec() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.authenticateOptional(any())).thenReturn(Optional.empty());
        when(submitStageService.submit(any())).thenReturn(acceptedCommand(id));

        MockHttpServletResponse response = submit(fixtureBody("signature-vector.json"));

        assertThat(response.getStatus()).isEqualTo(202);
        assertMatchesSpec(response);
        assertThat(response.getHeader("Location")).isEqualTo("/api/v1/commands/" + id);
    }

    // codes that the controller, the mapper or SubmitStageService can throw for this endpoint
    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {
            "INVALID_REQUEST", "SCHEMA_VIOLATION", "DEADLINE_EXPIRED", "DEADLINE_TOO_FAR",
            "INVALID_SIGNATURE", "SIGNER_MISMATCH", "FORBIDDEN", "NOT_FOUND",
            "UNEXPECTED_STAGE", "STAGE_ALREADY_PENDING", "STAGE_ALREADY_COMPLETED",
            "PROFILE_VIOLATION", "DEPENDENCY_UNAVAILABLE"})
    void domainErrorsMatchSpec(ErrorCode code) throws Exception {
        when(authService.authenticateOptional(any())).thenReturn(Optional.empty());
        when(submitStageService.submit(any())).thenThrow(new DomainException(code, "test", 1800001000L));

        MockHttpServletResponse response = submit(fixtureBody("signature-vector.json"));

        assertMatchesSpec(response);
        assertThat(MAPPER.readTree(response.getContentAsString()).path("code").asString()).isEqualTo(code.name());
        Integer documentedStatus = documentedStatusOf(code);
        if (documentedStatus != null) {
            assertThat(response.getStatus()).as("status for %s", code).isEqualTo(documentedStatus);
        }
    }

    // the spec names codes in response descriptions, e.g. 422 "... (`PROFILE_VIOLATION`)"
    private static Integer documentedStatusOf(ErrorCode code) {
        Pattern named = Pattern.compile("`" + code.name() + "`");
        Integer found = null;
        for (Map.Entry<String, JsonNode> entry : OPERATION.path("responses").properties()) {
            String description = SPEC.response(OPERATION, Integer.parseInt(entry.getKey())).path("description").asString();
            if (named.matcher(description).find()) {
                assertThat(found).as("%s is named under two statuses", code).isNull();
                found = Integer.parseInt(entry.getKey());
            }
        }
        return found;
    }

    @Test
    void invalidBearerTokenMatchesSpec() throws Exception {
        when(authService.authenticateOptional(any()))
                .thenThrow(new DomainException(ErrorCode.UNAUTHENTICATED, "token is not valid"));

        MockHttpServletResponse response = submit(fixtureBody("signature-vector.json"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertMatchesSpec(response);
    }

    @Test
    void malformedBodyMatchesSpec() throws Exception {
        MockHttpServletResponse response = submit("{\"report\": ");

        assertThat(response.getStatus()).isEqualTo(400);
        assertMatchesSpec(response);
    }

    @Test
    void unknownFieldMatchesSpec() throws Exception {
        ObjectNode body = (ObjectNode) MAPPER.readTree(fixtureBody("signature-vector.json"));
        body.put("nonce", 1);

        MockHttpServletResponse response = submit(body.toString());

        assertThat(response.getStatus()).isEqualTo(400);
        assertMatchesSpec(response);
    }

    // the request bodies in shared/fixtures are what the simulator sends, so they must match the spec too
    static Stream<String> fixturesWithSubmission() throws IOException {
        try (Stream<Path> files = Files.list(fixturesDir())) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("report-") && name.endsWith(".json"))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest
    @MethodSource("fixturesWithSubmission")
    void fixtureRequestBodiesMatchSpec(String fixture) throws Exception {
        Schema request = SPEC.schema(OPERATION.at("/requestBody/content/application~1json/schema/$ref").asString());

        assertThat(messages(request.validate(MAPPER.readTree(fixtureBody(fixture))))).isEmpty();
    }

    @Test
    void thereAreFixturesToCheck() throws IOException {
        assertThat(fixturesWithSubmission()).hasSizeGreaterThanOrEqualTo(5);
    }

    private MockHttpServletResponse submit(String body) throws Exception {
        String batchId = MAPPER.readTree(fixtureBody("signature-vector.json")).at("/report/batchId").asString();
        return mockMvc.perform(post(PATH, batchId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn()
                .getResponse();
    }

    private static void assertMatchesSpec(MockHttpServletResponse response) throws Exception {
        int status = response.getStatus();
        JsonNode documented = SPEC.response(OPERATION, status);
        assertThat(documented.isMissingNode())
                .as("status %d is not documented for POST %s", status, PATH)
                .isFalse();

        MediaType contentType = MediaType.parseMediaType(response.getContentType());
        String mediaType = contentType.getType() + "/" + contentType.getSubtype();
        JsonNode content = documented.path("content").path(mediaType);
        assertThat(content.isMissingNode())
                .as("status %d with %s is not documented, documented: %s",
                        status, mediaType, documented.path("content").propertyNames())
                .isFalse();

        Schema schema = SPEC.schema(content.path("schema").path("$ref").asString());
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(messages(schema.validate(body))).as("body %s", body).isEmpty();

        for (String header : documented.path("headers").propertyNames()) {
            assertThat(response.getHeader(header)).as("header %s", header).isNotNull();
        }
    }

    // a full POST body built from a fixture: the report plus the signature envelope
    private static String fixtureBody(String fixture) throws IOException {
        JsonNode vector = MAPPER.readTree(fixturesDir().resolve(fixture).toFile());
        ObjectNode body = MAPPER.createObjectNode();
        body.set("report", vector.get("report"));
        if (vector.has("submission")) {
            body.setAll((ObjectNode) vector.get("submission"));
        } else {
            body.put("stationCertificateId", "0x" + "11".repeat(32));
            body.put("reportHash", vector.get("reportHash").asString());
            body.put("deadline", vector.at("/stageCompletion/message/deadline").asLong());
            body.put("signature", vector.at("/stageCompletion/signature").asString());
        }
        return body.toString();
    }

    private static Path fixturesDir() {
        return OpenApiSpec.schemasDir().resolveSibling("fixtures");
    }

    private static StageCommand acceptedCommand(UUID id) {
        String station = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
        String hash = "0x" + "ab".repeat(32);
        return new StageCommand(id, CommandType.STAGE, station, hash, null, 2, station, hash, hash, hash,
                1800000600L, true, CommandStatus.ACCEPTED, null, null, null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));
    }

    private static List<String> messages(List<Error> errors) {
        return errors.stream().map(Error::toString).toList();
    }
}
