package pl.pz.supplyblockchain.api.dto;

import java.util.UUID;

public record AuthChallengeResponse(
        UUID challengeId,
        String message,
        long expiresAt) {
}
