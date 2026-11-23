package pl.pz.supplyblockchain.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.pz.supplyblockchain.application.ports.ConfirmedBlockTimeSource;
import pl.pz.supplyblockchain.application.ports.StationCertificateRepository;
import pl.pz.supplyblockchain.application.ports.StationCertificateVerifier;
import pl.pz.supplyblockchain.application.ports.StationRegistryException;
import pl.pz.supplyblockchain.domain.StationCertificate;
import pl.pz.supplyblockchain.domain.StationIdentityVerification;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class StageSubmissionIT {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String CERTIFICATE_ID =
            "0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e";
    private static final String OLD_DIGEST =
            "0x1111111111111111111111111111111111111111111111111111111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StationCertificateRepository certificateRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private StationCertificateVerifier certificateVerifier;

    @MockitoBean
    private ConfirmedBlockTimeSource confirmedBlockTime;

    private JsonNode vector;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM qa_approvals");
        jdbc.update("DELETE FROM transaction_attempts");
        jdbc.update("DELETE FROM commands");
        jdbc.update("DELETE FROM station_certificates");

        try (InputStream in = getClass().getResourceAsStream("/fixtures/station-certificate.json")) {
            JsonNode fixture = objectMapper.readTree(in);
            JsonNode certificate = fixture.get("certificate");
            String certificateJson = objectMapper.writeValueAsString(certificate);
            String certificateId = certificate.get("payload").get("certificateId").asString();
            String stationAddress = certificate.get("payload").get("stationAddress").asString();
            certificateRepository.save(new StationCertificate(
                    certificateId, stationAddress, certificateJson, Instant.now()));
        }
        try (InputStream in = getClass().getResourceAsStream("/fixtures/signature-vector.json")) {
            vector = objectMapper.readTree(in);
        }

        when(certificateVerifier.verifyForReport(any(), any(), anyInt())).thenReturn(
                new StationIdentityVerification(
                        StationIdentityVerification.Reason.OK, "ok", null, null, null));
    }

    @Test
    void submitStageReturns202AndTenReplaysReturnSameCommandId() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        String body = stageBody("stageCompletion");

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

        for (int replay = 0; replay < 10; replay++) {
            // Simulates a restarted process: the persistence context is empty between replays.
            entityManager.clear();
            mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.commandId").value(firstCommandId));
        }

        assertThat(commandCount(batchId)).isEqualTo(1);
    }

    @Test
    void replayAfterRevokedCertificateIsRejected() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        String body = stageBody("stageCompletion");

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        when(certificateVerifier.verifyForReport(any(), any(), anyInt())).thenReturn(
                new StationIdentityVerification(
                        StationIdentityVerification.Reason.REVOKED, "revoked", null, null, null));

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STATION_NOT_ACTIVE"));

        assertThat(commandCount(batchId)).isEqualTo(1);
    }

    @Test
    void replayWhenStationRegistryIsUnavailableReturns503() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        String body = stageBody("stageCompletion");

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        when(certificateVerifier.verifyForReport(any(), any(), anyInt())).thenThrow(
                new StationRegistryException(
                        StationRegistryException.Reason.UNAVAILABLE, "rpc down", null));

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

        assertThat(commandCount(batchId)).isEqualTo(1);
    }

    @Test
    void confirmedCommandWithoutOutcomeReturns409() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        int stageId = vector.get("report").get("stageId").asInt();
        insertCommand(UUID.randomUUID(), batchId, stageId, OLD_DIGEST, 1800000600L, null,
                "CONFIRMED", null, null, null);

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_ALREADY_COMPLETED"));

        assertThat(commandCount(batchId)).isEqualTo(1);
    }

    @Test
    void submitStageWithBadSignatureReturns403() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageApproval")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIGNER_MISMATCH"));

        assertThat(commandCount(batchId)).isZero();
    }

    @Test
    void negativeCertificateIsRejectedAndCreatesNoCommand() throws Exception {
        record Case(StationIdentityVerification.Reason reason, String code) {
        }
        List<Case> cases = List.of(
                new Case(StationIdentityVerification.Reason.UNTRUSTED_ISSUER, "CERTIFICATE_INVALID"),
                new Case(StationIdentityVerification.Reason.WRONG_DEPLOYMENT, "CERTIFICATE_INVALID"),
                new Case(StationIdentityVerification.Reason.NOT_YET_VALID, "CERTIFICATE_INVALID"),
                new Case(StationIdentityVerification.Reason.EXPIRED, "CERTIFICATE_INVALID"),
                new Case(StationIdentityVerification.Reason.REVOKED, "STATION_NOT_ACTIVE"),
                new Case(StationIdentityVerification.Reason.NOT_REGISTERED, "STATION_NOT_ACTIVE"),
                new Case(StationIdentityVerification.Reason.REGISTRY_MISMATCH, "REGISTRY_MISMATCH"),
                new Case(StationIdentityVerification.Reason.STAGE_NOT_ALLOWED, "STAGE_NOT_ALLOWED"));

        String batchId = vector.get("report").get("batchId").asString();
        String body = stageBody("stageCompletion");
        for (Case testCase : cases) {
            when(certificateVerifier.verifyForReport(any(), any(), anyInt())).thenReturn(
                    new StationIdentityVerification(testCase.reason(), "rejected", null, null, null));

            mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(testCase.code()));

            assertThat(commandCount(batchId)).as(testCase.reason().name()).isZero();
        }
    }

    @Test
    void stationRegistryUnavailabilityReturns503AndCreatesNoCommand() throws Exception {
        when(certificateVerifier.verifyForReport(any(), any(), anyInt())).thenThrow(
                new StationRegistryException(
                        StationRegistryException.Reason.UNAVAILABLE, "rpc down", null));
        String batchId = vector.get("report").get("batchId").asString();

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

        assertThat(commandCount(batchId)).isZero();
    }

    @Test
    void activeCommandWithDifferentContentReturns409() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        int stageId = vector.get("report").get("stageId").asInt();
        insertCommand(UUID.randomUUID(), batchId, stageId, OLD_DIGEST, 1800000600L, null,
                "ACCEPTED", null, null, null);

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_ALREADY_PENDING"));

        assertThat(commandCount(batchId)).isEqualTo(1);
    }

    @Test
    void failedCommandWithUnresolvedAttemptReturns409() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        int stageId = vector.get("report").get("stageId").asInt();
        UUID failedId = UUID.randomUUID();
        long deadline = 1800000600L;
        insertCommand(failedId, batchId, stageId, OLD_DIGEST, deadline, null,
                "FAILED", "DEADLINE_EXPIRED", null, null);
        insertAttempt(failedId, null);
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(deadline + 100);

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_ALREADY_PENDING"))
                .andExpect(jsonPath("$.retryAllowedAfter").value(deadline));

        assertThat(commandCount(batchId)).isEqualTo(1);
    }

    @Test
    void failedCommandWithDefiniteSendRejectionCanBeReplacedAfterDeadline() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        int stageId = vector.get("report").get("stageId").asInt();
        UUID failedId = UUID.randomUUID();
        long deadline = 1800000600L;
        insertCommand(failedId, batchId, stageId, OLD_DIGEST, deadline, null,
                "FAILED", "SEND_REJECTED", null, null);
        insertAttempt(failedId, null);
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(deadline + 1);

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(commandCount(batchId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM commands WHERE id = ?", String.class, failedId))
                .isEqualTo("FAILED");
    }

    @Test
    void failedCommandBeforeConfirmedDeadlineReturns409() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        int stageId = vector.get("report").get("stageId").asInt();
        UUID failedId = UUID.randomUUID();
        long deadline = 1800000600L;
        insertCommand(failedId, batchId, stageId, OLD_DIGEST, deadline, null,
                "FAILED", "DEADLINE_EXPIRED", null, null);
        insertAttempt(failedId, "SUCCESS");
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(deadline);

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_ALREADY_PENDING"))
                .andExpect(jsonPath("$.retryAllowedAfter").value(deadline));

        assertThat(commandCount(batchId)).isEqualTo(1);
    }

    @Test
    void failedCommandAfterConfirmedDeadlineIsReplaced() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        int stageId = vector.get("report").get("stageId").asInt();
        UUID failedId = UUID.randomUUID();
        long deadline = 1800000600L;
        insertCommand(failedId, batchId, stageId, OLD_DIGEST, deadline, null,
                "FAILED", "DEADLINE_EXPIRED", null, null);
        insertAttempt(failedId, "REVERTED");
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(deadline + 1);

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(commandCount(batchId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM commands WHERE id = ?", String.class, failedId))
                .isEqualTo("FAILED");
    }

    @Test
    void retryAllowedAfterRaisesTheFailedReplacementThreshold() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        int stageId = vector.get("report").get("stageId").asInt();
        UUID failedId = UUID.randomUUID();
        long deadline = 1800000600L;
        long retryAllowedAfter = 1800001200L;
        insertCommand(failedId, batchId, stageId, OLD_DIGEST, deadline, retryAllowedAfter,
                "FAILED", "PINNING_FAILED", null, null);
        insertAttempt(failedId, "SUCCESS");
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(deadline + 100);

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_ALREADY_PENDING"))
                .andExpect(jsonPath("$.retryAllowedAfter").value(retryAllowedAfter));

        assertThat(commandCount(batchId)).isEqualTo(1);
    }

    @Test
    void failedQaRejectedCommandIsReplacedImmediately() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        int stageId = vector.get("report").get("stageId").asInt();
        UUID failedId = UUID.randomUUID();
        insertCommand(failedId, batchId, stageId, OLD_DIGEST, 1800000600L, null,
                "FAILED", "DEADLINE_EXPIRED", null, null, false);
        when(confirmedBlockTime.confirmedTimestamp()).thenThrow(
                new pl.pz.supplyblockchain.application.ports.ChainUnavailableException("must not be read", null));

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(commandCount(batchId)).isEqualTo(2);
    }

    @Test
    void chainUnavailableDuringFailedReplacementReturns503() throws Exception {
        String batchId = vector.get("report").get("batchId").asString();
        int stageId = vector.get("report").get("stageId").asInt();
        UUID failedId = UUID.randomUUID();
        long deadline = 1800000600L;
        insertCommand(failedId, batchId, stageId, OLD_DIGEST, deadline, null,
                "FAILED", "DEADLINE_EXPIRED", null, null);
        insertAttempt(failedId, "SUCCESS");
        when(confirmedBlockTime.confirmedTimestamp()).thenThrow(
                new pl.pz.supplyblockchain.application.ports.ChainUnavailableException("rpc down", null));

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageBody("stageCompletion")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

        assertThat(commandCount(batchId)).isEqualTo(1);
    }

    private String stageBody(String signatureSection) throws Exception {
        JsonNode report = vector.get("report");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("report", report);
        body.put("stationCertificateId", CERTIFICATE_ID);
        body.put("reportHash", vector.get("reportHash").asString());
        body.put("deadline", vector.get(signatureSection).get("message").get("deadline").asLong());
        body.put("signature", vector.get(signatureSection).get("signature").asString());
        return objectMapper.writeValueAsString(body);
    }

    private int commandCount(String batchId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM commands WHERE batch_id = ?", Integer.class, batchId);
        return count == null ? 0 : count;
    }

    private void insertCommand(
            UUID id,
            String batchId,
            int stageId,
            String digest,
            long deadline,
            Long retryAllowedAfter,
            String status,
            String failureCode,
            String leaseOwner,
            Instant leaseUntil) {
        insertCommand(id, batchId, stageId, digest, deadline, retryAllowedAfter, status,
                failureCode, leaseOwner, leaseUntil, true);
    }

    private void insertCommand(
            UUID id,
            String batchId,
            int stageId,
            String digest,
            long deadline,
            Long retryAllowedAfter,
            String status,
            String failureCode,
            String leaseOwner,
            Instant leaseUntil,
            boolean qaPassed) {
        jdbc.update("""
                INSERT INTO commands (
                    id, chain_id, contract_address, type, owner_address, batch_id, stage_id,
                    station_address, station_certificate_id, report_hash, station_signature,
                    signed_payload_digest, deadline, qa_passed, status, failure_code,
                    retry_allowed_after, lease_owner, lease_until, version, created_at, updated_at)
                VALUES (?, 31337, ?, 'STAGE', ?, ?, ?, ?, ?, ?, '0xold-signature',
                        ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                id,
                CONTRACT,
                STATION,
                batchId,
                stageId,
                STATION,
                CERTIFICATE_ID,
                "0x" + "2".repeat(64),
                digest,
                deadline,
                qaPassed,
                status,
                failureCode,
                retryAllowedAfter,
                leaseOwner,
                leaseUntil == null ? null : Timestamp.from(leaseUntil),
                Timestamp.from(Instant.now().minusSeconds(60)),
                Timestamp.from(Instant.now().minusSeconds(60)));
    }

    private void insertAttempt(UUID commandId, String receiptStatus) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO transaction_attempts (
                    id, command_id, relayer_address, tx_nonce, tx_hash, signed_tx,
                    calldata_hash, max_priority_fee_per_gas, max_fee_per_gas, gas_limit,
                    broadcast_at, receipt_block, receipt_block_hash, receipt_status, created_at)
                VALUES (?, ?, ?, 1, ?, '0xsigned', ?, ?, ?, 21000, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                commandId,
                STATION,
                "0x" + suffix + suffix,
                "0x" + "3".repeat(64),
                new BigDecimal("30"),
                new BigDecimal("60"),
                Timestamp.from(Instant.now().minusSeconds(30)),
                receiptStatus == null ? null : 123L,
                receiptStatus == null ? null : "0x" + "4".repeat(64),
                receiptStatus,
                Timestamp.from(Instant.now().minusSeconds(30)));
    }
}
