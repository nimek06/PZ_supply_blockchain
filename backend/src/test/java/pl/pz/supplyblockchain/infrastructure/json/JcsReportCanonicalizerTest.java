package pl.pz.supplyblockchain.infrastructure.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.application.ports.CanonicalReport;
import pl.pz.supplyblockchain.domain.TelemetryReport;
import pl.pz.supplyblockchain.support.SignatureVectorFixture;
import tools.jackson.databind.json.JsonMapper;

class JcsReportCanonicalizerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JcsReportCanonicalizer canonicalizer = new JcsReportCanonicalizer(mapper);

    @Test
    void matchesSharedSignatureVector() throws Exception {
        SignatureVectorFixture vector = SignatureVectorFixture.load(mapper);
        TelemetryReport report = vector.telemetryReport(mapper);

        CanonicalReport result = canonicalizer.canonicalize(report);

        assertThat(new String(result.jcsBytes(), StandardCharsets.UTF_8))
                .isEqualTo(vector.canonicalReportJcs());
        assertThat(result.reportHash()).isEqualTo(vector.reportHash());
    }
}
