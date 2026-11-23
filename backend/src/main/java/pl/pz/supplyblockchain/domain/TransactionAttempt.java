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

    public TransactionAttempt withBroadcastAt(Instant broadcastAt) {
        return new TransactionAttempt(
                id, commandId, relayerAddress, txNonce, txHash, signedTx, calldataHash,
                maxPriorityFeePerGas, maxFeePerGas, gasLimit, broadcastAt,
                receiptBlock, receiptBlockHash, receiptStatus, createdAt);
    }

    public TransactionAttempt withReceipt(
            long blockNumber, String blockHash, TransactionReceiptStatus status) {
        return new TransactionAttempt(
                id, commandId, relayerAddress, txNonce, txHash, signedTx, calldataHash,
                maxPriorityFeePerGas, maxFeePerGas, gasLimit, broadcastAt,
                blockNumber, blockHash, status, createdAt);
    }
}
