package pl.pz.supplyblockchain.application.ports;

import java.util.Optional;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.QaApproval;

public interface QaApprovalRepository {

    QaApproval save(QaApproval approval);

    Optional<QaApproval> findActiveByCommandId(UUID commandId);

    Optional<QaApproval> findById(UUID id);
}
