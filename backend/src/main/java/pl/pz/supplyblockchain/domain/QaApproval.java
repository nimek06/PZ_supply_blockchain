package pl.pz.supplyblockchain.domain;

import java.time.Instant;
import java.util.UUID;

public record QaApproval(
        UUID id,
        UUID commandId,
        String operatorAddress,
        long approvalDeadline,
        String signature,
        QaApprovalStatus status,
        Instant createdAt) {
}
