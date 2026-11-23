package pl.pz.supplyblockchain.application.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.TransactionAttempt;

public interface TransactionAttemptRepository {

    TransactionAttempt save(TransactionAttempt attempt);

    Optional<TransactionAttempt> findById(UUID id);

    Optional<TransactionAttempt> findByTxHash(String txHash);

    List<TransactionAttempt> findByCommandId(UUID commandId);

    /**
     * True when an attempt may still land. Any attempt without a recorded receipt is unresolved,
     * including one that was signed but not confirmed as sent before a restart.
     */
    boolean hasUnresolvedAttempts(UUID commandId);
}
