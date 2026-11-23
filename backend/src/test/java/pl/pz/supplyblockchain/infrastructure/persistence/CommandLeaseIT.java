package pl.pz.supplyblockchain.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pl.pz.supplyblockchain.application.ports.CommandLeaseRepository;
import pl.pz.supplyblockchain.domain.StageCommand;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class CommandLeaseIT {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);

    @Autowired
    private CommandLeaseRepository leases;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM qa_approvals");
        jdbc.update("DELETE FROM transaction_attempts");
        jdbc.update("DELETE FROM commands");
    }

    @Test
    void acquiresOldestEligibleAndSetsLease() {
        UUID failed = insert("FAILED", NOW.minusSeconds(600), null, null, null);
        UUID confirmed = insert("CONFIRMED", NOW.minusSeconds(500), null, null, null);
        UUID waiting = insert("WAITING_FOR_QA", NOW.minusSeconds(400), null, null, null);
        UUID submitted = insert("SUBMITTED", NOW.minusSeconds(300), null, null, null);
        UUID accepted = insert("ACCEPTED", NOW.minusSeconds(120), null, null, null);
        UUID ready = insert("READY_TO_SUBMIT", NOW.minusSeconds(60), null, null, null);

        Optional<StageCommand> first = leases.acquireNext("worker-1", NOW, LEASE);

        assertThat(first).isPresent();
        assertThat(first.get().id()).isEqualTo(accepted);
        assertThat(first.get().status().name()).isEqualTo("ACCEPTED");
        assertThat(first.get().id()).isNotIn(failed, confirmed, waiting, submitted, ready);
        assertThat(leaseOwner(accepted)).isEqualTo("worker-1");
        assertThat(leaseUntil(accepted)).isEqualTo(NOW.plus(LEASE));
        assertThat(updatedAt(accepted)).isEqualTo(NOW);
        assertThat(version(accepted)).isEqualTo(1);

        Optional<StageCommand> second = leases.acquireNext("worker-2", NOW, LEASE);

        assertThat(second).isPresent();
        assertThat(second.get().id()).isEqualTo(ready);
    }

    @Test
    void skipsLeasedCommandsAndFutureRetries() {
        UUID leased = insert("ACCEPTED", NOW.minusSeconds(300), null, "other-worker", NOW.plusSeconds(60));
        UUID retryLater = insert("ACCEPTED", NOW.minusSeconds(200), NOW.plusSeconds(60), null, null);
        UUID due = insert("PINNING", NOW.minusSeconds(100), NOW.minusSeconds(1), null, null);

        Optional<StageCommand> acquired = leases.acquireNext("worker-1", NOW, LEASE);

        assertThat(acquired).isPresent();
        assertThat(acquired.get().id()).isEqualTo(due);
        assertThat(leaseOwner(leased)).isEqualTo("other-worker");
        assertThat(leaseOwner(retryLater)).isNull();
    }

    @Test
    void expiredLeaseCanBeTakenOver() {
        UUID expired = insert("READY_TO_SUBMIT", NOW.minusSeconds(100), null, "dead-worker", NOW.minusSeconds(1));

        Optional<StageCommand> acquired = leases.acquireNext("worker-1", NOW, LEASE);

        assertThat(acquired).isPresent();
        assertThat(acquired.get().id()).isEqualTo(expired);
        assertThat(leaseOwner(expired)).isEqualTo("worker-1");
        assertThat(leaseUntil(expired)).isEqualTo(NOW.plus(LEASE));
    }

    @Test
    void ignoresIneligibleStatusesAndFutureRetries() {
        insert("FAILED", NOW.minusSeconds(500), null, null, null);
        insert("CONFIRMED", NOW.minusSeconds(400), null, null, null);
        insert("WAITING_FOR_QA", NOW.minusSeconds(300), null, null, null);
        insert("SUBMITTED", NOW.minusSeconds(200), null, null, null);
        insert("ACCEPTED", NOW.minusSeconds(100), NOW.plusSeconds(60), null, null);

        assertThat(leases.acquireNext("worker-1", NOW, LEASE)).isEmpty();
    }

    @Test
    void releaseFreesOnlyTheHeldLease() {
        UUID id = insert("READY_TO_SUBMIT", NOW.minusSeconds(100), null, "worker-1", NOW.plusSeconds(60));

        assertThat(leases.release(id, "worker-2", NOW)).isFalse();
        assertThat(leaseOwner(id)).isEqualTo("worker-1");

        assertThat(leases.release(id, "worker-1", NOW)).isTrue();
        assertThat(leaseOwner(id)).isNull();
        assertThat(leaseUntil(id)).isNull();
        assertThat(updatedAt(id)).isEqualTo(NOW);
        assertThat(version(id)).isEqualTo(1);

        assertThat(leases.release(id, "worker-1", NOW)).isFalse();
    }

    @Test
    void releaseDoesNotReviveAnExpiredLease() {
        UUID id = insert("ACCEPTED", NOW.minusSeconds(100), null, "worker-1", NOW.minusSeconds(1));

        assertThat(leases.release(id, "worker-1", NOW)).isFalse();
        assertThat(leaseOwner(id)).isEqualTo("worker-1");
    }

    @Test
    void skipLockedLeavesARowLockedByAnotherWorker() throws Exception {
        UUID locked = insert("ACCEPTED", NOW.minusSeconds(200), null, null, null);
        UUID next = insert("ACCEPTED", NOW.minusSeconds(100), null, null, null);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id FROM commands WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, locked);
                statement.executeQuery().close();
            }

            Optional<StageCommand> acquired = leases.acquireNext("worker-1", NOW, LEASE);

            assertThat(acquired).isPresent();
            assertThat(acquired.get().id()).isEqualTo(next);
            assertThat(leaseOwner(locked)).isNull();
            connection.rollback();
        }
    }

    private UUID insert(
            String status,
            Instant createdAt,
            Instant retryAt,
            String leaseOwner,
            Instant leaseUntil) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commands (
                    id, chain_id, contract_address, type, owner_address, batch_id, stage_id,
                    station_address, station_certificate_id, report_hash, station_signature,
                    signed_payload_digest, deadline, qa_passed, status, retry_at,
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
                "0x" + id.toString().replace("-", "") + "11",
                "0x" + id.toString().replace("-", "") + "22",
                status,
                retryAt == null ? null : Timestamp.from(retryAt),
                leaseOwner,
                leaseUntil == null ? null : Timestamp.from(leaseUntil),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        return id;
    }

    private String leaseOwner(UUID id) {
        return jdbc.queryForObject(
                "SELECT lease_owner FROM commands WHERE id = ?", String.class, id);
    }

    private Instant leaseUntil(UUID id) {
        Timestamp value = jdbc.queryForObject(
                "SELECT lease_until FROM commands WHERE id = ?", Timestamp.class, id);
        return value == null ? null : value.toInstant();
    }

    private Instant updatedAt(UUID id) {
        return jdbc.queryForObject(
                "SELECT updated_at FROM commands WHERE id = ?", Timestamp.class, id).toInstant();
    }

    private long version(UUID id) {
        Long value = jdbc.queryForObject(
                "SELECT version FROM commands WHERE id = ?", Long.class, id);
        return value == null ? -1 : value;
    }
}
