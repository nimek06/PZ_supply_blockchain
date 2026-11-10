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
}
