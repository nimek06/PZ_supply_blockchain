package pl.pz.supplyblockchain.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthChallengeJpaRepository extends JpaRepository<AuthChallengeEntity, UUID> {

    @Modifying
    @Query("""
            update AuthChallengeEntity challenge
               set challenge.usedAt = :now
             where challenge.id = :id
               and challenge.usedAt is null
               and challenge.expiresAt > :now
            """)
    int consume(@Param("id") UUID id, @Param("now") Instant now);
}
