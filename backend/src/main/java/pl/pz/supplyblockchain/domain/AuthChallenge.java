package pl.pz.supplyblockchain.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthChallenge(
        UUID id,
        String address,
        String nonce,
        String message,
        String domain,
        String uri,
        long chainId,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt) {
}
