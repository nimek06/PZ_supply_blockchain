package pl.pz.supplyblockchain.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.pz.supplyblockchain.application.ports.StationCertificateRepository;
import pl.pz.supplyblockchain.application.ports.StationCertificateVerifier;
import pl.pz.supplyblockchain.domain.StationCertificate;
import pl.pz.supplyblockchain.domain.StationIdentityVerification;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class StageSubmissionIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StationCertificateRepository certificateRepository;

    @MockitoBean
    private StationCertificateVerifier certificateVerifier;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode fixture;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/station-certificate.json")) {
            fixture = objectMapper.readTree(in);
        }
        JsonNode certificate = fixture.get("certificate");
        String certificateJson = objectMapper.writeValueAsString(certificate);
        String certificateId = certificate.get("payload").get("certificateId").asString();
        String stationAddress = certificate.get("payload").get("stationAddress").asString();
        certificateRepository.save(new StationCertificate(
                certificateId, stationAddress, certificateJson, Instant.now()));

        when(certificateVerifier.verifyForReport(any(), any(), anyInt())).thenReturn(
                new StationIdentityVerification(
                        StationIdentityVerification.Reason.OK, "ok", null, null, null));
    }

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
                        "stationCertificateId", objectMapper.getNodeFactory().textNode("0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e"),
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
                        "stationCertificateId", objectMapper.getNodeFactory().textNode("0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e"),
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
