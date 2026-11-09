package pl.pz.supplyblockchain.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface QaApprovalJpaRepository extends JpaRepository<QaApprovalEntity, UUID> {

    Optional<QaApprovalEntity> findFirstByCommandIdAndStatusOrderByCreatedAtDesc(
            UUID commandId, pl.pz.supplyblockchain.domain.QaApprovalStatus status);
}
