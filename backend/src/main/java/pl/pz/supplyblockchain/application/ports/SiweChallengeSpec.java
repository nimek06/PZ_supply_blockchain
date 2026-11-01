package pl.pz.supplyblockchain.application.ports;

import java.time.Instant;

public record SiweChallengeSpec(
        String domain,
        String address,
        String uri,
        long chainId,
        String nonce,
        String statement,
        Instant issuedAt,
        Instant expiresAt) {
}
