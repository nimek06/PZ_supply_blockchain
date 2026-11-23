package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.SubmittedAttempt;
import pl.pz.supplyblockchain.application.ports.SubmittedRecoveryRepository;

/**
 * Claims SUBMITTED commands that still have an unresolved or reverted attempt. The row lock and lease
 * update are one short transaction; the receipt lookup and possible resend happen after it returns.
 */
@Repository
public class JpaSubmittedRecoveryRepository implements SubmittedRecoveryRepository {

    private static final String CLAIM_SQL = """
            SELECT c.id,
                   (SELECT a.id
                      FROM transaction_attempts a
                     WHERE a.command_id = c.id
                       AND (a.receipt_status IS NULL OR a.receipt_status = 'REVERTED')
                     ORDER BY a.created_at DESC
                     LIMIT 1)
              FROM commands c
             WHERE c.status = 'SUBMITTED'
               AND (c.lease_until IS NULL OR c.lease_until <= :now)
               AND (c.retry_at IS NULL OR c.retry_at <= :now)
               AND EXISTS (
                   SELECT 1
                     FROM transaction_attempts a
                    WHERE a.command_id = c.id
                      AND (a.receipt_status IS NULL OR a.receipt_status = 'REVERTED'))
             ORDER BY c.updated_at, c.id
             FOR UPDATE OF c SKIP LOCKED
             LIMIT 1
            """;

    private static final String LEASE_SQL = """
            UPDATE commands
               SET lease_owner = :owner,
                   lease_until = :until,
                   updated_at = :now,
                   version = version + 1
             WHERE id = :id
            """;

    private final EntityManager entityManager;
    private final TransactionAttemptJpaRepository attemptRepository;

    public JpaSubmittedRecoveryRepository(
            EntityManager entityManager, TransactionAttemptJpaRepository attemptRepository) {
        this.entityManager = entityManager;
        this.attemptRepository = attemptRepository;
    }

    @Override
    @Transactional
    public Optional<SubmittedAttempt> claimNext(
            String leaseOwner, Instant now, Duration leaseDuration) {
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner is required");
        }
        if (now == null || leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("now and a positive leaseDuration are required");
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(CLAIM_SQL)
                .setParameter("now", offset(now))
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.getFirst();
        UUID commandId = toUuid(row[0]);
        UUID attemptId = toUuid(row[1]);
        if (attemptId == null) {
            return Optional.empty();
        }

        int updated = entityManager.createNativeQuery(LEASE_SQL)
                .setParameter("owner", leaseOwner)
                .setParameter("until", offset(now.plus(leaseDuration)))
                .setParameter("now", offset(now))
                .setParameter("id", commandId)
                .executeUpdate();
        if (updated != 1) {
            return Optional.empty();
        }

        StageCommandEntity command = entityManager.find(StageCommandEntity.class, commandId);
        if (command == null) {
            return Optional.empty();
        }
        entityManager.refresh(command);
        Optional<SubmittedAttempt> result = attemptRepository.findById(attemptId)
                .map(attempt -> new SubmittedAttempt(command.toDomain(), attempt.toDomain()));
        return result;
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        return UUID.fromString(value.toString());
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
