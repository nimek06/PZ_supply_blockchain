package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.AuthChallenge;

@Entity
@Table(name = "auth_challenges")
class AuthChallengeEntity {

    @Id
    private UUID id;

    @Column(name = "address", nullable = false, length = 42)
    private String address;

    @Column(name = "nonce", nullable = false, length = 64)
    private String nonce;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "domain", nullable = false, length = 253)
    private String domain;

    @Column(name = "uri", nullable = false)
    private String uri;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected AuthChallengeEntity() {
    }

    static AuthChallengeEntity from(AuthChallenge challenge) {
        AuthChallengeEntity entity = new AuthChallengeEntity();
        entity.id = challenge.id();
        entity.address = challenge.address();
        entity.nonce = challenge.nonce();
        entity.message = challenge.message();
        entity.domain = challenge.domain();
        entity.uri = challenge.uri();
        entity.chainId = challenge.chainId();
        entity.issuedAt = challenge.issuedAt();
        entity.expiresAt = challenge.expiresAt();
        entity.usedAt = challenge.usedAt();
        return entity;
    }

    AuthChallenge toDomain() {
        return new AuthChallenge(
                id, address, nonce, message, domain, uri, chainId, issuedAt, expiresAt, usedAt);
    }
}
