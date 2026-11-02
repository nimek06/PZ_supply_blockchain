package pl.pz.supplyblockchain.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import pl.pz.supplyblockchain.domain.TelemetryReport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SignatureVectorFixture(
        JsonNode report,
        String canonicalReportJcs,
        String reportHash,
        StageCompletion stageCompletion,
        StageApproval stageApproval) {

    public static SignatureVectorFixture load(ObjectMapper mapper) throws IOException {
        try (InputStream input = SignatureVectorFixture.class.getResourceAsStream(
                "/fixtures/signature-vector.json")) {
            if (input == null) {
                throw new IllegalStateException("signature vector fixture is missing");
            }
            return mapper.readValue(input, SignatureVectorFixture.class);
        }
    }

    public TelemetryReport telemetryReport(ObjectMapper mapper) {
        return mapper.treeToValue(report, TelemetryReport.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageCompletion(
            String typeString,
            JsonNode message,
            String digest,
            String signature,
            String signer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageApproval(
            String typeString,
            JsonNode message,
            String digest,
            String signature,
            String signer) {
    }
}
