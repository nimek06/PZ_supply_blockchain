package pl.pz.supplyblockchain.application.ports;

import java.util.Optional;
import pl.pz.supplyblockchain.domain.DeploymentId;

public interface BatchProjectionRepository {

    Optional<BatchProjection> findByBatchId(DeploymentId deploymentId, String batchId);

    Optional<BatchProjection> findByBatchNumber(DeploymentId deploymentId, String batchNumber);
}
