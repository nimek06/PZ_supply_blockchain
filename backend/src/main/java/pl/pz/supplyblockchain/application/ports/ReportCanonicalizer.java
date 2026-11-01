package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.TelemetryReport;

public interface ReportCanonicalizer {

    CanonicalReport canonicalize(TelemetryReport report);
}
