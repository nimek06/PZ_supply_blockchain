package pl.pz.supplyblockchain.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.QaApprovalRepository;
import pl.pz.supplyblockchain.domain.QaApproval;
import pl.pz.supplyblockchain.domain.QaApprovalStatus;

@Repository
public class JpaQaApprovalRepository implements QaApprovalRepository {

    private final QaApprovalJpaRepository repository;

    public JpaQaApprovalRepository(QaApprovalJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public QaApproval save(QaApproval approval) {
        return repository.save(QaApprovalEntity.from(approval)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QaApproval> findActiveByCommandId(UUID commandId) {
        return repository
                .findFirstByCommandIdAndStatusOrderByCreatedAtDesc(commandId, QaApprovalStatus.PENDING)
                .map(QaApprovalEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QaApproval> findById(UUID id) {
        return repository.findById(id).map(QaApprovalEntity::toDomain);
    }
}
