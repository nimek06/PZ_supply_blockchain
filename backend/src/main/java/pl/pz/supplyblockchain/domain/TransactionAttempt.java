package pl.pz.supplyblockchain.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionAttempt(
        UUID id,
        UUID commandId,
        String relayerAddress,
        BigDecimal txNonce,
        String txHash,
        String signedTx,
        String calldataHash,
        BigDecimal maxPriorityFeePerGas,
        BigDecimal maxFeePerGas,
        BigDecimal gasLimit,
        Instant broadcastAt,
        Long receiptBlock,
        String receiptBlockHash,
        TransactionReceiptStatus receiptStatus,
        Instant createdAt) {
}
