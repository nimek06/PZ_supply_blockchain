package pl.pz.supplyblockchain.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record StageSubmissionRequest(
        TelemetryReportRequest report,
        String stationCertificateId,
        String reportHash,
        Long deadline,
        String signature) {
}
