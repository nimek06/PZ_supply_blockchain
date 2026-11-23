package pl.pz.supplyblockchain.application.ports;

public record ChainEvent(
        String eventType,
        long blockNumber,
        String blockHash,
        long transactionIndex,
        long logIndex,
        String transactionHash,
        String batchId,
        String batchNumber,
        Integer stageId,
        String stationAddress,
        String qaOperator,
        String reportHash,
        String cid,
        String payloadJson) {
}
