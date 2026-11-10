package pl.pz.supplyblockchain.application.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StageCommand;

public interface CommandRepository {

    Optional<StageCommand> findById(UUID id);

    Optional<StageCommand> findBySignedPayloadDigest(DeploymentId deploymentId, String digest);

    Optional<StageCommand> findLatestStageCommand(DeploymentId deploymentId, String batchId, int stageId);

    StageCommand saveNew(NewStageCommand command);

    StageCommand saveNewCreateBatch(NewCreateBatchCommand command);

    Optional<StageCommand> findCreateBatch(DeploymentId deploymentId, String batchId);

    Optional<StageCommand> findCreateBatchByNumber(DeploymentId deploymentId, String batchNumber);

    Optional<BatchIdempotency> findCreateBatchByIdempotency(
            DeploymentId deploymentId, String ownerAddress, String idempotencyKey);

    List<Integer> findConfirmedAcceptedStageIds(DeploymentId deploymentId, String batchId);
}
