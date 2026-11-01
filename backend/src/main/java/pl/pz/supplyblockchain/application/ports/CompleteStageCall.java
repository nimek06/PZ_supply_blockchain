package pl.pz.supplyblockchain.application.ports;

public record CompleteStageCall(
        String batchId,
        int stageId,
        String reportHash,
        boolean qaPassed,
        long deadline,
        String ipfsCid,
        String stationSignature,
        long approvalDeadline,
        String approvalSignature) {
}
