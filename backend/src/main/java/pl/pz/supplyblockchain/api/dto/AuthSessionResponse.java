package pl.pz.supplyblockchain.api.dto;

public record AuthSessionResponse(
        String token,
        String tokenType,
        String address,
        long expiresAt,
        long idleTimeoutSeconds) {
}
