package pl.pz.supplyblockchain.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pl.pz.supplyblockchain.application.ports.CommandLifecycleRepository;
import pl.pz.supplyblockchain.application.ports.LeaseLostException;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.StageCommand;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class CommandLifecycleIT {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final String OWNER = "worker-1";

    @Autowired
    private CommandLifecycleRepository lifecycle;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM qa_approvals");
        jdbc.update("DELETE FROM transaction_attempts");
        jdbc.update("DELETE FROM commands");
    }

    @Test
    void workerTransitionsAcceptedToPinning() {
        UUID id = insert("ACCEPTED", OWNER, NOW.plusSeconds(60));

        StageCommand result = lifecycle.transition(
                id, OWNER, CommandStatus.ACCEPTED, CommandStatus.PINNING,
                null, null, null, null, NOW);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.status()).isEqualTo(CommandStatus.PINNING);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(version(id)).isEqualTo(1);
    }

    @Test
    void stageTransitionToQaStatusRequiresCid() {
        UUID id = insert("ACCEPTED", OWNER, NOW.plusSeconds(60));

        assertThatThrownBy(() -> lifecycle.transition(
                id, OWNER, CommandStatus.ACCEPTED, CommandStatus.WAITING_FOR_QA,
                null, null, null, null, NOW))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        assertThat(status(id)).isEqualTo("ACCEPTED");

        StageCommand waiting = lifecycle.transition(
                id, OWNER, CommandStatus.ACCEPTED, CommandStatus.WAITING_FOR_QA,
                "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi",
                null, null, null, NOW);

        assertThat(waiting.status()).isEqualTo(CommandStatus.WAITING_FOR_QA);
        assertThat(waiting.cid()).startsWith("bafybeig");
    }

    @Test
    void readyTransitionUsesCidAlreadyStoredByQaWaitingState() {
        UUID id = insert("WAITING_FOR_QA", OWNER, NOW.plusSeconds(60));
        String cid = "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi";
        jdbc.update("UPDATE commands SET cid = ? WHERE id = ?", cid, id);

        StageCommand ready = lifecycle.transition(
                id, OWNER, CommandStatus.WAITING_FOR_QA, CommandStatus.READY_TO_SUBMIT,
                null, null, null, null, NOW);

        assertThat(ready.status()).isEqualTo(CommandStatus.READY_TO_SUBMIT);
        assertThat(ready.cid()).isEqualTo(cid);
    }

    @Test
    void transitionClearsStaleFailureAndRetryFields() {
        UUID id = insert("PINNING", OWNER, NOW.plusSeconds(60));
        jdbc.update("""
                UPDATE commands
                   SET failure_code = 'TEMP_RPC', retry_allowed_after = 123, retry_at = ?
                 WHERE id = ?
                """, Timestamp.from(NOW.plusSeconds(60)), id);

        StageCommand ready = lifecycle.transition(
                id, OWNER, CommandStatus.PINNING, CommandStatus.READY_TO_SUBMIT,
                "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi",
                null, null, null, NOW);

        assertThat(ready.failureCode()).isNull();
        assertThat(ready.retryAllowedAfter()).isNull();
        assertThat(ready.retryAt()).isNull();
    }

    @Test
    void failedTransitionRequiresFailureCodeAndStoresRetryFields() {
        UUID id = insert("PINNING", OWNER, NOW.plusSeconds(60));

        assertThatThrownBy(() -> lifecycle.transition(
                id, OWNER, CommandStatus.PINNING, CommandStatus.FAILED,
                null, null, null, null, NOW))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        StageCommand failed = lifecycle.transition(
                id, OWNER, CommandStatus.PINNING, CommandStatus.FAILED,
                null, "PINNING_FAILED", 1800001200L, NOW.plusSeconds(120), NOW);

        assertThat(failed.status()).isEqualTo(CommandStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("PINNING_FAILED");
        assertThat(failed.retryAllowedAfter()).isEqualTo(1800001200L);
        assertThat(failed.retryAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void confirmedAndAcceptedAreNotWorkerTargets() {
        UUID id = insert("ACCEPTED", OWNER, NOW.plusSeconds(60));

        assertThatThrownBy(() -> lifecycle.transition(
                id, OWNER, CommandStatus.ACCEPTED, CommandStatus.CONFIRMED,
                "bafy", null, null, null, NOW))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> lifecycle.transition(
                id, OWNER, CommandStatus.ACCEPTED, CommandStatus.ACCEPTED,
                null, null, null, null, NOW))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        assertThat(status(id)).isEqualTo("ACCEPTED");
    }

    @Test
    void wrongOwnerLosesLease() {
        UUID id = insert("ACCEPTED", OWNER, NOW.plusSeconds(60));

        assertThatThrownBy(() -> lifecycle.transition(
                id, "worker-2", CommandStatus.ACCEPTED, CommandStatus.PINNING,
                null, null, null, null, NOW))
                .isInstanceOf(LeaseLostException.class);

        assertThat(status(id)).isEqualTo("ACCEPTED");
    }

    @Test
    void expiredLeaseLosesLease() {
        UUID id = insert("ACCEPTED", OWNER, NOW.minusSeconds(1));

        assertThatThrownBy(() -> lifecycle.transition(
                id, OWNER, CommandStatus.ACCEPTED, CommandStatus.PINNING,
                null, null, null, null, NOW))
                .isInstanceOf(LeaseLostException.class);

        assertThat(status(id)).isEqualTo("ACCEPTED");
    }

    @Test
    void unexpectedStatusLosesLease() {
        UUID id = insert("ACCEPTED", OWNER, NOW.plusSeconds(60));

        assertThatThrownBy(() -> lifecycle.transition(
                id, OWNER, CommandStatus.READY_TO_SUBMIT, CommandStatus.SUBMITTED,
                null, null, null, null, NOW))
                .isInstanceOf(LeaseLostException.class);

        assertThat(status(id)).isEqualTo("ACCEPTED");
    }

    @Test
    void missingCommandLosesLease() {
        assertThatThrownBy(() -> lifecycle.transition(
                UUID.randomUUID(), OWNER, CommandStatus.ACCEPTED, CommandStatus.PINNING,
                null, null, null, null, NOW))
                .isInstanceOf(LeaseLostException.class);
    }

    @Test
    void submittedTransitionIsAllowedFromReadyToSubmit() {
        UUID id = insert("READY_TO_SUBMIT", OWNER, NOW.plusSeconds(60));

        StageCommand submitted = lifecycle.transition(
                id, OWNER, CommandStatus.READY_TO_SUBMIT, CommandStatus.SUBMITTED,
                null, null, null, null, NOW);

        assertThat(submitted.status()).isEqualTo(CommandStatus.SUBMITTED);
    }

    private UUID insert(String status, String leaseOwner, Instant leaseUntil) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commands (
                    id, chain_id, contract_address, type, owner_address, batch_id, stage_id,
                    station_address, station_certificate_id, report_hash, station_signature,
                    signed_payload_digest, deadline, qa_passed, status,
                    lease_owner, lease_until, version, created_at, updated_at)
                VALUES (?, 31337, ?, 'STAGE', ?, ?, 0, ?, ?, ?, '0xsig', ?, 1800000600,
                        TRUE, ?, ?, ?, 0, ?, ?)
                """,
                id,
                CONTRACT,
                STATION,
                "0x" + id.toString().replace("-", "") + "00",
                STATION,
                "0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e",
                "0x" + id.toString().replace("-", "") + "22",
                "0x" + id.toString().replace("-", "") + "11",
                status,
                leaseOwner,
                Timestamp.from(leaseUntil),
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(60)));
        return id;
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM commands WHERE id = ?", String.class, id);
    }

    private long version(UUID id) {
        Long value = jdbc.queryForObject("SELECT version FROM commands WHERE id = ?", Long.class, id);
        return value == null ? -1 : value;
    }
}
