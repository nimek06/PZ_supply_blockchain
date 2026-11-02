package pl.pz.supplyblockchain.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthSessionJpaRepository extends JpaRepository<AuthSessionEntity, UUID> {

    Optional<AuthSessionEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update AuthSessionEntity session set session.lastSeenAt = :now where session.id = :id")
    int touch(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("""
            update AuthSessionEntity session
               set session.revokedAt = :now
             where session.id = :id
               and session.revokedAt is null
            """)
    int revoke(@Param("id") UUID id, @Param("now") Instant now);
}
