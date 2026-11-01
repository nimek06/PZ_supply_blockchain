package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StageCommand;

public record NewStageCommand(
        DeploymentId deploymentId,
        StageCommand command,
        byte[] canonicalReportJcs,
        String stationSignature,
        String reportJson) {
}
