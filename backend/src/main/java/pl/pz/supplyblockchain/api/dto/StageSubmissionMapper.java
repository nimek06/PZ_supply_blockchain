package pl.pz.supplyblockchain.api.dto;

import java.util.Optional;
import pl.pz.supplyblockchain.application.commands.SubmitStageCommand;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.TelemetryReport;

public final class StageSubmissionMapper {

    private StageSubmissionMapper() {
    }

    public static SubmitStageCommand toCommand(
            String pathBatchId, StageSubmissionRequest request, Optional<String> authenticatedAddress) {
        if (request == null) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "request body is required");
        }
        if (request.deadline() == null) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "deadline is required");
        }
        if (request.report() == null) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "report is required");
        }
        return new SubmitStageCommand(
                pathBatchId,
                toDomain(request.report()),
                request.stationCertificateId(),
                request.reportHash(),
                request.deadline(),
                request.signature(),
                authenticatedAddress);
    }

    private static TelemetryReport toDomain(TelemetryReportRequest report) {
        require(report.schemaVersion(), "report.schemaVersion");
        require(report.batchId(), "report.batchId");
        require(report.stageId(), "report.stageId");
        require(report.stationAddress(), "report.stationAddress");
        require(report.timestamp(), "report.timestamp");
        require(report.measurements(), "report.measurements");
        require(report.qaPassed(), "report.qaPassed");
        return new TelemetryReport(
                report.schemaVersion(),
                report.batchId(),
                report.stageId(),
                report.stationAddress(),
                report.timestamp(),
                report.measurements(),
                report.qaPassed());
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, field + " is required");
        }
    }
}
