package pl.pz.supplyblockchain.api.dto;

public record BatchResponse(
        String batchId,
        String batchNumber,
        boolean completed,
        Integer nextStage,
        ProjectionFreshnessResponse projection) {
}
