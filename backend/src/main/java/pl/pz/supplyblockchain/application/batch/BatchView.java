package pl.pz.supplyblockchain.application.batch;

public record BatchView(
        String batchId,
        String batchNumber,
        boolean completed,
        Integer nextStage,
        ProjectionFreshness projection) {

    public record ProjectionFreshness(
            long lastIndexedBlock,
            long requiredConfirmations,
            boolean stale) {
    }
}
