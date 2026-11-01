package pl.pz.supplyblockchain.api.dto;

import java.util.UUID;

public record CommandStatusResponse(
        UUID commandId,
        String type,
        String status,
        String outcome,
        String batchId,
        String batchNumber,
        Integer stageId,
        String reportHash,
        String cid,
        String txHash,
        String failureCode,
        Long retryAt,
        Long retryAllowedAfter,
        Long deadline,
        Long createdAt,
        Long updatedAt) {
}
