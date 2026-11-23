package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.CommandLifecycleRepository;
import pl.pz.supplyblockchain.application.ports.LeaseLostException;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.StageCommand;

/**
 * Lease-guarded status transitions for the worker. The indexer is the only writer of
 * {@code CONFIRMED}; this adapter rejects it together with {@code ACCEPTED}.
 */
@Repository
public class JpaCommandLifecycleRepository implements CommandLifecycleRepository {

    private static final Set<CommandStatus> WORKER_TARGETS = EnumSet.of(
            CommandStatus.PINNING,
            CommandStatus.WAITING_FOR_QA,
            CommandStatus.READY_TO_SUBMIT,
            CommandStatus.SUBMITTED,
            CommandStatus.FAILED);

    private static final Set<CommandStatus> EXPECTED_STATUSES = EnumSet.of(
            CommandStatus.ACCEPTED,
            CommandStatus.PINNING,
            CommandStatus.WAITING_FOR_QA,
            CommandStatus.READY_TO_SUBMIT,
            CommandStatus.SUBMITTED);

    private static final String TRANSITION_SQL = """
            UPDATE commands
               SET status = :nextStatus,
                   cid = COALESCE(CAST(:cid AS VARCHAR), cid),
                   failure_code = CASE
                       WHEN CAST(:nextStatus AS VARCHAR) = 'FAILED'
                       THEN CAST(:failureCode AS VARCHAR)
                       ELSE NULL
                   END,
                   retry_allowed_after = CASE
                       WHEN CAST(:nextStatus AS VARCHAR) = 'FAILED'
                       THEN CAST(:retryAllowedAfter AS BIGINT)
                       ELSE NULL
                   END,
                   retry_at = CAST(:retryAt AS TIMESTAMPTZ),
                   updated_at = :now,
                   version = version + 1
             WHERE id = :id
               AND lease_owner = :owner
               AND lease_until > :now
               AND status = :expectedStatus
            """;

    private final EntityManager entityManager;

    public JpaCommandLifecycleRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public StageCommand transition(
            UUID commandId,
            String leaseOwner,
            CommandStatus expectedStatus,
            CommandStatus nextStatus,
            String cid,
            String failureCode,
            Long retryAllowedAfter,
            Instant retryAt,
            Instant now) {
        validate(commandId, leaseOwner, expectedStatus, nextStatus, cid, failureCode,
                retryAllowedAfter, retryAt, now);

        StageCommandEntity entity = entityManager.find(StageCommandEntity.class, commandId);
        if (entity == null) {
            throw new LeaseLostException("command does not exist");
        }
        StageCommand current = entity.toDomain();
        String effectiveCid = cid == null ? current.cid() : cid;
        if (current.type() == CommandType.STAGE
                && (nextStatus == CommandStatus.WAITING_FOR_QA || nextStatus == CommandStatus.READY_TO_SUBMIT)
                && (effectiveCid == null || effectiveCid.isBlank())) {
            throw new IllegalArgumentException("a STAGE transition to " + nextStatus + " requires a CID");
        }

        int updated = entityManager.createNativeQuery(TRANSITION_SQL)
                .setParameter("nextStatus", nextStatus.name())
                .setParameter("cid", blankToNull(cid))
                .setParameter("failureCode", blankToNull(failureCode))
                .setParameter("retryAllowedAfter", retryAllowedAfter)
                .setParameter("retryAt", retryAt == null ? null : offset(retryAt))
                .setParameter("now", offset(now))
                .setParameter("id", commandId)
                .setParameter("owner", leaseOwner)
                .setParameter("expectedStatus", expectedStatus.name())
                .executeUpdate();
        if (updated != 1) {
            throw new LeaseLostException("lease is no longer held or the status changed");
        }

        entityManager.refresh(entity);
        return entity.toDomain();
    }

    private static void validate(
            UUID commandId,
            String leaseOwner,
            CommandStatus expectedStatus,
            CommandStatus nextStatus,
            String cid,
            String failureCode,
            Long retryAllowedAfter,
            Instant retryAt,
            Instant now) {
        if (commandId == null) {
            throw new IllegalArgumentException("commandId is required");
        }
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner is required");
        }
        if (expectedStatus == null || !EXPECTED_STATUSES.contains(expectedStatus)) {
            throw new IllegalArgumentException("expectedStatus is not a worker-managed status");
        }
        if (nextStatus == null || !WORKER_TARGETS.contains(nextStatus)) {
            throw new IllegalArgumentException("nextStatus is not a worker-managed status");
        }
        if (nextStatus == CommandStatus.FAILED && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("FAILED requires a failureCode");
        }
        if (retryAllowedAfter != null && retryAllowedAfter < 0) {
            throw new IllegalArgumentException("retryAllowedAfter must not be negative");
        }
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (cid != null && cid.isBlank()) {
            throw new IllegalArgumentException("cid must not be blank when provided");
        }
        if (retryAt != null && retryAt.isBefore(now)) {
            throw new IllegalArgumentException("retryAt must not be in the past");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
