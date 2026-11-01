package pl.pz.supplyblockchain.application.commands;

import java.util.Optional;
import pl.pz.supplyblockchain.domain.TelemetryReport;

public record SubmitStageCommand(
        String pathBatchId,
        TelemetryReport report,
        String stationCertificateId,
        String reportHash,
        long deadline,
        String signature,
        Optional<String> authenticatedAddress) {
}
