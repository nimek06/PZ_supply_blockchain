package pl.pz.supplyblockchain.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
import pl.pz.supplyblockchain.application.ports.CommandSubmissionRepository;
import pl.pz.supplyblockchain.application.ports.LeaseLostException;
import pl.pz.supplyblockchain.domain.TransactionAttempt;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class CommandSubmissionIT {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    private CommandSubmissionRepository submissions;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM qa_approvals");
        jdbc.update("DELETE FROM transaction_attempts");
        jdbc.update("DELETE FROM commands");
    }

    @Test
    void reservesTheAttemptAndSubmittedStatusTogether() {
        UUID commandId = insert("READY_TO_SUBMIT", "worker-1", NOW.plusSeconds(60));
        TransactionAttempt attempt = attempt(commandId);

        submissions.reserveAndMarkSubmitted(attempt, "worker-1", NOW);

        assertThat(status(commandId)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transaction_attempts WHERE command_id = ?",
                Integer.class, commandId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT signed_tx FROM transaction_attempts WHERE command_id = ?",
                String.class, commandId)).isEqualTo(attempt.signedTx());
        assertThat(updatedAt(commandId)).isEqualTo(NOW);
        assertThat(version(commandId)).isEqualTo(1);
    }

    @Test
    void wrongOwnerCannotReserveOrSend() {
        UUID commandId = insert("READY_TO_SUBMIT", "other-worker", NOW.plusSeconds(60));

        assertThatThrownBy(() -> submissions.reserveAndMarkSubmitted(attempt(commandId), "worker-1", NOW))
                .isInstanceOf(LeaseLostException.class);

        assertThat(status(commandId)).isEqualTo("READY_TO_SUBMIT");
        assertThat(attemptCount(commandId)).isZero();
    }

    @Test
    void nonReadyStatusCannotReserveOrSend() {
        UUID commandId = insert("ACCEPTED", "worker-1", NOW.plusSeconds(60));

        assertThatThrownBy(() -> submissions.reserveAndMarkSubmitted(attempt(commandId), "worker-1", NOW))
                .isInstanceOf(LeaseLostException.class);

        assertThat(status(commandId)).isEqualTo("ACCEPTED");
        assertThat(attemptCount(commandId)).isZero();
    }

    @Test
    void expiredLeaseCannotReserveOrSend() {
        UUID commandId = insert("READY_TO_SUBMIT", "worker-1", NOW.minusSeconds(1));

        assertThatThrownBy(() -> submissions.reserveAndMarkSubmitted(attempt(commandId), "worker-1", NOW))
                .isInstanceOf(LeaseLostException.class);

        assertThat(status(commandId)).isEqualTo("READY_TO_SUBMIT");
        assertThat(attemptCount(commandId)).isZero();
    }

    private TransactionAttempt attempt(UUID commandId) {
        String suffix = commandId.toString().replace("-", "");
        return new TransactionAttempt(
                UUID.randomUUID(), commandId, STATION,
                BigDecimal.valueOf(5), "0x" + suffix + suffix, "0xraw", "0x" + "a".repeat(64),
                BigDecimal.valueOf(30), BigDecimal.valueOf(60), BigDecimal.valueOf(21000),
                null, null, null, null, NOW);
    }

    private UUID insert(String status, String leaseOwner, Instant leaseUntil) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commands (
                    id, chain_id, contract_address, type, owner_address, batch_number, batch_id,
                    station_signature, signed_payload_digest, deadline, qa_passed, status,
                    lease_owner, lease_until, version, created_at, updated_at)
                VALUES (?, 31337, ?, 'CREATE_BATCH', ?, ?, ?, '0xsig', ?, 1800000600, FALSE, ?,
                        ?, ?, 0, ?, ?)
                """,
                id,
                CONTRACT,
                STATION,
                "B-2026-0001",
                "0x" + id.toString().replace("-", "") + "00",
                "0x" + id.toString().replace("-", "") + "11",
                status,
                leaseOwner,
                Timestamp.from(leaseUntil),
                Timestamp.from(NOW.minusSeconds(10)),
                Timestamp.from(NOW.minusSeconds(10)));
        return id;
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM commands WHERE id = ?", String.class, id);
    }

    private int attemptCount(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM transaction_attempts WHERE command_id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    private Instant updatedAt(UUID id) {
        return jdbc.queryForObject(
                "SELECT updated_at FROM commands WHERE id = ?", Timestamp.class, id).toInstant();
    }

    private long version(UUID id) {
        Long value = jdbc.queryForObject("SELECT version FROM commands WHERE id = ?", Long.class, id);
        return value == null ? -1 : value;
    }
}
