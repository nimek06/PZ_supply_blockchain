package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.TransactionAttempt;
import pl.pz.supplyblockchain.domain.TransactionReceiptStatus;

@Entity
@Table(name = "transaction_attempts")
class TransactionAttemptEntity {

    @Id
    private UUID id;

    @Column(name = "command_id", nullable = false)
    private UUID commandId;

    @Column(name = "relayer_address", nullable = false, length = 42)
    private String relayerAddress;

    @Column(name = "tx_nonce", nullable = false, precision = 78, scale = 0)
    private BigDecimal txNonce;

    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Column(name = "signed_tx", nullable = false)
    private String signedTx;

    @Column(name = "calldata_hash", nullable = false, length = 66)
    private String calldataHash;

    @Column(name = "max_priority_fee_per_gas", nullable = false, precision = 78, scale = 0)
    private BigDecimal maxPriorityFeePerGas;

    @Column(name = "max_fee_per_gas", nullable = false, precision = 78, scale = 0)
    private BigDecimal maxFeePerGas;

    @Column(name = "gas_limit", precision = 78, scale = 0)
    private BigDecimal gasLimit;

    @Column(name = "broadcast_at")
    private Instant broadcastAt;

    @Column(name = "receipt_block")
    private Long receiptBlock;

    @Column(name = "receipt_block_hash", length = 66)
    private String receiptBlockHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", length = 16)
    private TransactionReceiptStatus receiptStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransactionAttemptEntity() {
    }

    static TransactionAttemptEntity from(TransactionAttempt attempt) {
        TransactionAttemptEntity entity = new TransactionAttemptEntity();
        entity.id = attempt.id();
        entity.commandId = attempt.commandId();
        entity.relayerAddress = attempt.relayerAddress();
        entity.txNonce = attempt.txNonce();
        entity.txHash = attempt.txHash();
        entity.signedTx = attempt.signedTx();
        entity.calldataHash = attempt.calldataHash();
        entity.maxPriorityFeePerGas = attempt.maxPriorityFeePerGas();
        entity.maxFeePerGas = attempt.maxFeePerGas();
        entity.gasLimit = attempt.gasLimit();
        entity.broadcastAt = attempt.broadcastAt();
        entity.receiptBlock = attempt.receiptBlock();
        entity.receiptBlockHash = attempt.receiptBlockHash();
        entity.receiptStatus = attempt.receiptStatus();
        entity.createdAt = attempt.createdAt();
        return entity;
    }

    TransactionAttempt toDomain() {
        return new TransactionAttempt(
                id, commandId, relayerAddress, txNonce, txHash, signedTx, calldataHash,
                maxPriorityFeePerGas, maxFeePerGas, gasLimit, broadcastAt,
                receiptBlock, receiptBlockHash, receiptStatus, createdAt);
    }
}
