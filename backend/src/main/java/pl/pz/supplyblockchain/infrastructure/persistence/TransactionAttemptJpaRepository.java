package pl.pz.supplyblockchain.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TransactionAttemptJpaRepository extends JpaRepository<TransactionAttemptEntity, UUID> {

    Optional<TransactionAttemptEntity> findByTxHash(String txHash);

    List<TransactionAttemptEntity> findByCommandIdOrderByCreatedAtDesc(UUID commandId);
}
