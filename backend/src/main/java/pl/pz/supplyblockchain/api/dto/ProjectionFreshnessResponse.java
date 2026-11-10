package pl.pz.supplyblockchain.api.dto;

public record ProjectionFreshnessResponse(
        long lastIndexedBlock,
        long requiredConfirmations,
        boolean stale) {
}
