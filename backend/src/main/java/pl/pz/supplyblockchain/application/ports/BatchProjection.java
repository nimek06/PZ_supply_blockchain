package pl.pz.supplyblockchain.application.ports;

public record BatchProjection(
        String batchId,
        String batchNumber,
        int nextStage,
        boolean completed,
        long lastIndexedBlock,
        boolean stale) {
}
