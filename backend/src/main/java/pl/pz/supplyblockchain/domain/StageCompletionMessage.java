package pl.pz.supplyblockchain.domain;

public record StageCompletionMessage(
        String batchId,
        int stageId,
        String reportHash,
        boolean qaPassed,
        long deadline) {
}
