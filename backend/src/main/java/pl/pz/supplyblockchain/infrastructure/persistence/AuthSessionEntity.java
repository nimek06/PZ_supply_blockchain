package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.AuthSession;

@Entity
@Table(name = "auth_sessions")
class AuthSessionEntity {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "address", nullable = false, length = 42)
    private String address;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "idle_timeout_seconds", nullable = false)
    private long idleTimeoutSeconds;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSessionEntity() {
    }

    static AuthSessionEntity from(AuthSession session) {
        AuthSessionEntity entity = new AuthSessionEntity();
        entity.id = session.id();
        entity.tokenHash = session.tokenHash();
        entity.address = session.address();
        entity.chainId = session.chainId();
        entity.createdAt = session.createdAt();
        entity.expiresAt = session.expiresAt();
        entity.idleTimeoutSeconds = session.idleTimeoutSeconds();
        entity.lastSeenAt = session.lastSeenAt();
        entity.revokedAt = session.revokedAt();
        return entity;
    }

    AuthSession toDomain() {
        return new AuthSession(
                id,
                tokenHash,
                address,
                chainId,
                createdAt,
                expiresAt,
                idleTimeoutSeconds,
                lastSeenAt,
                revokedAt);
    }
}
