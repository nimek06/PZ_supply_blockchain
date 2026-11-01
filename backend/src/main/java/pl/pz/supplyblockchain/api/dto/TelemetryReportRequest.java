package pl.pz.supplyblockchain.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TelemetryReportRequest(
        Integer schemaVersion,
        String batchId,
        Integer stageId,
        String stationAddress,
        Long timestamp,
        @JsonDeserialize(using = MeasurementsDeserializer.class) Map<String, Long> measurements,
        Boolean qaPassed) {
}
