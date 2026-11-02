package pl.pz.supplyblockchain.application.ports;

import java.util.Optional;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StageCommand;

public interface CommandRepository {

    Optional<StageCommand> findById(UUID id);

    Optional<StageCommand> findBySignedPayloadDigest(DeploymentId deploymentId, String digest);

    Optional<StageCommand> findLatestStageCommand(DeploymentId deploymentId, String batchId, int stageId);

    StageCommand saveNew(NewStageCommand command);
}
