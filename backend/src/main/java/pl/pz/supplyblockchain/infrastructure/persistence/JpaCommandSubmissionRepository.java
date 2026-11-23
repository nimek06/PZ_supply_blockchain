package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.CommandSubmissionRepository;
import pl.pz.supplyblockchain.application.ports.LeaseLostException;
import pl.pz.supplyblockchain.domain.TransactionAttempt;

/**
 * The attempt row and the transition to SUBMITTED are committed together before broadcast. If the
 * lease or expected status changed, the insert is rolled back and nothing is sent.
 */
@Repository
public class JpaCommandSubmissionRepository implements CommandSubmissionRepository {

    private static final String INSERT_SQL = """
            INSERT INTO transaction_attempts (
                id, command_id, relayer_address, tx_nonce, tx_hash, signed_tx, calldata_hash,
                max_priority_fee_per_gas, max_fee_per_gas, gas_limit, broadcast_at,
                receipt_block, receipt_block_hash, receipt_status, created_at)
            VALUES (
                :id, :commandId, :relayerAddress, :nonce, :txHash, :signedTx, :calldataHash,
                :maxPriorityFeePerGas, :maxFeePerGas, :gasLimit, :broadcastAt,
                :receiptBlock, :receiptBlockHash, :receiptStatus, :createdAt)
            """;

    private static final String SUBMIT_SQL = """
            UPDATE commands
               SET status = 'SUBMITTED',
                   updated_at = :now,
                   version = version + 1
             WHERE id = :id
               AND lease_owner = :owner
               AND lease_until > :now
               AND status = 'READY_TO_SUBMIT'
            """;

    private final EntityManager entityManager;

    public JpaCommandSubmissionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public TransactionAttempt reserveAndMarkSubmitted(
            TransactionAttempt attempt, String leaseOwner, Instant now) {
        if (attempt == null || attempt.commandId() == null) {
            throw new IllegalArgumentException("attempt and commandId are required");
        }
        if (leaseOwner == null || leaseOwner.isBlank() || now == null) {
            throw new IllegalArgumentException("leaseOwner and now are required");
        }

        entityManager.createNativeQuery(INSERT_SQL)
                .setParameter("id", attempt.id())
                .setParameter("commandId", attempt.commandId())
                .setParameter("relayerAddress", attempt.relayerAddress())
                .setParameter("nonce", attempt.txNonce())
                .setParameter("txHash", attempt.txHash())
                .setParameter("signedTx", attempt.signedTx())
                .setParameter("calldataHash", attempt.calldataHash())
                .setParameter("maxPriorityFeePerGas", attempt.maxPriorityFeePerGas())
                .setParameter("maxFeePerGas", attempt.maxFeePerGas())
                .setParameter("gasLimit", attempt.gasLimit())
                .setParameter("broadcastAt", attempt.broadcastAt() == null
                        ? null : offset(attempt.broadcastAt()))
                .setParameter("receiptBlock", attempt.receiptBlock())
                .setParameter("receiptBlockHash", attempt.receiptBlockHash())
                .setParameter("receiptStatus", attempt.receiptStatus() == null
                        ? null : attempt.receiptStatus().name())
                .setParameter("createdAt", offset(attempt.createdAt()))
                .executeUpdate();

        int updated = entityManager.createNativeQuery(SUBMIT_SQL)
                .setParameter("now", offset(now))
                .setParameter("id", attempt.commandId())
                .setParameter("owner", leaseOwner)
                .executeUpdate();
        if (updated != 1) {
            throw new LeaseLostException("lease is no longer held or command is not READY_TO_SUBMIT");
        }
        return attempt;
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
