package pl.pz.supplyblockchain.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class StageSubmissionIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitStageReturns202AndIdempotentReplay() throws Exception {
        JsonNode vector;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/signature-vector.json")) {
            vector = objectMapper.readTree(in);
        }
        JsonNode report = vector.get("report");
        String batchId = report.get("batchId").asString();
        String body = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .setAll(java.util.Map.of(
                        "report", report,
                        "stationCertificateId", objectMapper.getNodeFactory().textNode("0xcert000000000000000000000000000000000000000000000000000000000001"),
                        "reportHash", objectMapper.getNodeFactory().textNode(vector.get("reportHash").asString()),
                        "deadline", objectMapper.getNodeFactory().numberNode(vector.get("stageCompletion").get("message").get("deadline").asLong()),
                        "signature", objectMapper.getNodeFactory().textNode(vector.get("stageCompletion").get("signature").asString())
                )));

        MvcResult first = mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.commandId").isNotEmpty())
                .andExpect(jsonPath("$.type").value("STAGE"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.txHash").doesNotExist())
                .andReturn();

        String firstCommandId = objectMapper.readTree(
                first.getResponse().getContentAsString()).get("commandId").asString();

        // Idempotent replay returns same commandId
        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.commandId").value(firstCommandId));
    }

    @Test
    void submitStageWithBadSignatureReturns403() throws Exception {
        JsonNode vector;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/signature-vector.json")) {
            vector = objectMapper.readTree(in);
        }
        JsonNode report = vector.get("report");
        String batchId = report.get("batchId").asString();
        // Use approval signature instead of completion → signer mismatch
        String body = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .setAll(java.util.Map.of(
                        "report", report,
                        "stationCertificateId", objectMapper.getNodeFactory().textNode("0xcert000000000000000000000000000000000000000000000000000000000001"),
                        "reportHash", objectMapper.getNodeFactory().textNode(vector.get("reportHash").asString()),
                        "deadline", objectMapper.getNodeFactory().numberNode(vector.get("stageApproval").get("message").get("deadline").asLong()),
                        "signature", objectMapper.getNodeFactory().textNode(vector.get("stageApproval").get("signature").asString())
                )));

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIGNER_MISMATCH"));
    }
}
