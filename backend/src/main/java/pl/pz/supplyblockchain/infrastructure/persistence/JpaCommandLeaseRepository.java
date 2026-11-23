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
import pl.pz.supplyblockchain.application.ports.CommandLeaseRepository;
import pl.pz.supplyblockchain.domain.StageCommand;

/**
 * PostgreSQL queue claims for the single local worker. The row lock and the lease update happen in
 * one short transaction; no network I/O is performed here.
 */
@Repository
public class JpaCommandLeaseRepository implements CommandLeaseRepository {

    private static final String ELIGIBLE_STATUSES = "'ACCEPTED', 'PINNING', 'READY_TO_SUBMIT'";

    private static final String CLAIM_SQL = """
            SELECT id
              FROM commands
             WHERE status IN (%s)
               AND (lease_until IS NULL OR lease_until <= :now)
               AND (retry_at IS NULL OR retry_at <= :now)
             ORDER BY created_at, id
             FOR UPDATE SKIP LOCKED
             LIMIT 1
            """.formatted(ELIGIBLE_STATUSES);

    private static final String LEASE_SQL = """
            UPDATE commands
               SET lease_owner = :owner,
                   lease_until = :until,
                   updated_at = :now,
                   version = version + 1
             WHERE id = :id
            """;

    private static final String RELEASE_SQL = """
            UPDATE commands
               SET lease_owner = NULL,
                   lease_until = NULL,
                   updated_at = :now,
                   version = version + 1
             WHERE id = :id
               AND lease_owner = :owner
               AND lease_until > :now
            """;

    private final EntityManager entityManager;

    public JpaCommandLeaseRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Optional<StageCommand> acquireNext(String leaseOwner, Instant now, Duration leaseDuration) {
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner is required");
        }
        if (now == null || leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("now and a positive leaseDuration are required");
        }

        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(CLAIM_SQL)
                .setParameter("now", offset(now))
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UUID id = toUuid(rows.getFirst());

        int updated = entityManager.createNativeQuery(LEASE_SQL)
                .setParameter("owner", leaseOwner)
                .setParameter("until", offset(now.plus(leaseDuration)))
                .setParameter("now", offset(now))
                .setParameter("id", id)
                .executeUpdate();
        if (updated != 1) {
            return Optional.empty();
        }

        StageCommandEntity entity = entityManager.find(StageCommandEntity.class, id);
        if (entity == null) {
            return Optional.empty();
        }
        entityManager.refresh(entity);
        return Optional.of(entity.toDomain());
    }

    @Override
    @Transactional
    public boolean release(UUID commandId, String leaseOwner, Instant now) {
        if (commandId == null || leaseOwner == null || leaseOwner.isBlank() || now == null) {
            return false;
        }
        int updated = entityManager.createNativeQuery(RELEASE_SQL)
                .setParameter("id", commandId)
                .setParameter("owner", leaseOwner)
                .setParameter("now", offset(now))
                .executeUpdate();
        return updated == 1;
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            throw new IllegalStateException("command id query returned null");
        }
        return UUID.fromString(value.toString());
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
