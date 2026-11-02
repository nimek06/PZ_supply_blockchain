package pl.pz.supplyblockchain.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthSession(
        UUID id,
        String tokenHash,
        String address,
        long chainId,
        Instant createdAt,
        Instant expiresAt,
        long idleTimeoutSeconds,
        Instant lastSeenAt,
        Instant revokedAt) {
}
