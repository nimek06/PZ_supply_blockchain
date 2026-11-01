package pl.pz.supplyblockchain.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public record TelemetryReport(
        int schemaVersion,
        String batchId,
        int stageId,
        String stationAddress,
        long timestamp,
        Map<String, Long> measurements,
        boolean qaPassed) {

    private static final Pattern BYTES32 = Pattern.compile("^0x[0-9a-f]{64}$");
    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-f]{40}$");

    public TelemetryReport {
        if (schemaVersion != 1) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "schemaVersion must be 1");
        }
        if (batchId == null || !BYTES32.matcher(batchId).matches()) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION,
                    "report.batchId must be a lowercase bytes32 hex value");
        }
        if (stageId < 0 || stageId > 3) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "report.stageId must be between 0 and 3");
        }
        if (stationAddress == null || !ADDRESS.matcher(stationAddress).matches()) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION,
                    "report.stationAddress must be a lowercase 20-byte hex address");
        }
        if (timestamp < 0) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "report.timestamp must not be negative");
        }
        if (measurements == null || measurements.isEmpty()) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "report.measurements must not be empty");
        }
        Map<String, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : measurements.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new DomainException(ErrorCode.SCHEMA_VIOLATION,
                        "report.measurements keys must not be blank");
            }
            if (entry.getValue() == null) {
                throw new DomainException(ErrorCode.SCHEMA_VIOLATION,
                        "report.measurements values must be integers");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        measurements = Collections.unmodifiableMap(copy);
    }

    public String normalizedBatchId() {
        return batchId.toLowerCase(Locale.ROOT);
    }
}
