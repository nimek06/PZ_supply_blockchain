package pl.pz.supplyblockchain.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.TransactionAttemptRepository;
import pl.pz.supplyblockchain.domain.TransactionAttempt;

@Repository
public class JpaTransactionAttemptRepository implements TransactionAttemptRepository {

    private final TransactionAttemptJpaRepository repository;

    public JpaTransactionAttemptRepository(TransactionAttemptJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public TransactionAttempt save(TransactionAttempt attempt) {
        return repository.save(TransactionAttemptEntity.from(attempt)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionAttempt> findById(UUID id) {
        return repository.findById(id).map(TransactionAttemptEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionAttempt> findByTxHash(String txHash) {
        return repository.findByTxHash(txHash).map(TransactionAttemptEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAttempt> findByCommandId(UUID commandId) {
        return repository.findByCommandIdOrderByCreatedAtDesc(commandId).stream()
                .map(TransactionAttemptEntity::toDomain)
                .toList();
    }
}
