package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StageCommand;

public record NewCreateBatchCommand(
        DeploymentId deploymentId,
        StageCommand command,
        String idempotencyKey,
        String requestHash) {
}
