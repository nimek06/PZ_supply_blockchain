package pl.pz.supplyblockchain.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pl.pz.supplyblockchain.application.ports.SubmittedAttempt;
import pl.pz.supplyblockchain.application.ports.SubmittedRecoveryRepository;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class SubmittedRecoveryIT {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);

    @Autowired
    private SubmittedRecoveryRepository recovery;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM qa_approvals");
        jdbc.update("DELETE FROM transaction_attempts");
        jdbc.update("DELETE FROM commands");
    }

    @Test
    void claimsSubmittedCommandWithUnresolvedAttempt() {
        UUID commandId = insertCommand("SUBMITTED", null, null);
        insertAttempt(commandId, null);

        Optional<SubmittedAttempt> claimed = recovery.claimNext("worker-1", NOW, LEASE);

        assertThat(claimed).isPresent();
        assertThat(claimed.get().command().id()).isEqualTo(commandId);
        assertThat(claimed.get().attempt().receiptStatus()).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT lease_owner FROM commands WHERE id = ?", String.class, commandId))
                .isEqualTo("worker-1");
    }

    @Test
    void activeLeaseIsNotClaimedTwice() {
        UUID commandId = insertCommand("SUBMITTED", "worker-1", NOW.plusSeconds(60));
        insertAttempt(commandId, null);

        assertThat(recovery.claimNext("worker-2", NOW, LEASE)).isEmpty();
    }

    @Test
    void expiredLeaseCanBeRecovered() {
        UUID commandId = insertCommand("SUBMITTED", "dead-worker", NOW.minusSeconds(1));
        insertAttempt(commandId, null);

        Optional<SubmittedAttempt> claimed = recovery.claimNext("worker-1", NOW, LEASE);

        assertThat(claimed).isPresent();
        assertThat(claimed.get().command().id()).isEqualTo(commandId);
    }

    @Test
    void futureRetryAtIsNotClaimed() {
        UUID commandId = insertCommand("SUBMITTED", null, NOW.plusSeconds(60));
        jdbc.update("UPDATE commands SET retry_at = ? WHERE id = ?",
                Timestamp.from(NOW.plusSeconds(60)), commandId);
        insertAttempt(commandId, null);

        assertThat(recovery.claimNext("worker-1", NOW, LEASE)).isEmpty();
    }

    @Test
    void revertedAttemptIsStillRecoverableToFailTheCommand() {
        UUID commandId = insertCommand("SUBMITTED", null, null);
        insertAttempt(commandId, "REVERTED");

        Optional<SubmittedAttempt> claimed = recovery.claimNext("worker-1", NOW, LEASE);

        assertThat(claimed).isPresent();
        assertThat(claimed.get().attempt().receiptStatus().name()).isEqualTo("REVERTED");
    }

    @Test
    void waitedOrConfirmedCommandsAreNotRecovered() {
        UUID waiting = insertCommand("WAITING_FOR_QA", null, null);
        UUID confirmed = insertCommand("CONFIRMED", null, null);
        insertAttempt(waiting, null);
        insertAttempt(confirmed, null);

        assertThat(recovery.claimNext("worker-1", NOW, LEASE)).isEmpty();
    }

    private UUID insertCommand(String status, String leaseOwner, Instant leaseUntil) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commands (
                    id, chain_id, contract_address, type, owner_address, batch_id, stage_id,
                    station_address, station_certificate_id, report_hash, station_signature,
                    signed_payload_digest, deadline, qa_passed, status, cid,
                    lease_owner, lease_until, version, created_at, updated_at)
                VALUES (?, 31337, ?, 'STAGE', ?, ?, 0, ?, ?, ?, '0xsig', ?, 1800000600,
                        TRUE, ?, ?, ?, ?, 0, ?, ?)
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
                "bafy-test",
                leaseOwner,
                leaseUntil == null ? null : Timestamp.from(leaseUntil),
                Timestamp.from(NOW.minusSeconds(10)),
                Timestamp.from(NOW.minusSeconds(10)));
        return id;
    }

    private void insertAttempt(UUID commandId, String receiptStatus) {
        String suffix = commandId.toString().replace("-", "");
        jdbc.update("""
                INSERT INTO transaction_attempts (
                    id, command_id, relayer_address, tx_nonce, tx_hash, signed_tx, calldata_hash,
                    max_priority_fee_per_gas, max_fee_per_gas, gas_limit, broadcast_at,
                    receipt_block, receipt_block_hash, receipt_status, created_at)
                VALUES (?, ?, ?, 1, ?, '0xraw', ?, 30, 60, 21000, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                commandId,
                STATION,
                "0x" + suffix + suffix,
                "0x" + "a".repeat(64),
                Timestamp.from(NOW.minusSeconds(5)),
                receiptStatus == null ? null : 9L,
                receiptStatus == null ? null : "0x" + "9".repeat(64),
                receiptStatus,
                Timestamp.from(NOW.minusSeconds(5)));
    }
}
