package pl.pz.supplyblockchain.domain;

public record StageApprovalMessage(
        String batchId,
        int stageId,
        String reportHash,
        long deadline) {
}
