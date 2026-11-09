package pl.pz.supplyblockchain.domain;

import java.time.Instant;
import java.util.UUID;

public record StageCommand(
        UUID id,
        CommandType type,
        String ownerAddress,
        String batchId,
        String batchNumber,
        Integer stageId,
        String stationAddress,
        String stationCertificateId,
        String reportHash,
        String signedPayloadDigest,
        Long deadline,
        Boolean qaPassed,
        CommandStatus status,
        CommandOutcome outcome,
        String cid,
        String failureCode,
        Instant retryAt,
        Long retryAllowedAfter,
        Instant createdAt,
        Instant updatedAt) {
}
