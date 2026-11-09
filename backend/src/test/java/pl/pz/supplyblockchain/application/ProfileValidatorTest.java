package pl.pz.supplyblockchain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.TelemetryReport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProfileValidatorTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ProfileValidator validator = new ProfileValidator(mapper);

    @Test
    void acceptsValidStage2Report() throws Exception {
        TelemetryReport report = loadReport("/fixtures/signature-vector.json");

        assertThatNoException().isThrownBy(() -> validator.validate(report));
    }

    @Test
    void acceptsQaFailReportThatMatchesProfile() throws Exception {
        TelemetryReport report = loadReport("/fixtures/report-qa-fail.json");

        assertThatNoException().isThrownBy(() -> validator.validate(report));
    }

    @Test
    void rejectsStage0ReportWithIncompleteChecklist() throws Exception {
        TelemetryReport report = loadReport("/fixtures/report-profile-violation-stage0.json");

        assertThatThrownBy(() -> validator.validate(report))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.PROFILE_VIOLATION));
    }

    @Test
    void rejectsStage1ReportWithIncompleteOperations() throws Exception {
        TelemetryReport report = loadReport("/fixtures/report-profile-violation-stage1.json");

        assertThatThrownBy(() -> validator.validate(report))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.PROFILE_VIOLATION));
    }

    @Test
    void rejectsStage2ReportWithDefects() throws Exception {
        TelemetryReport report = loadReport("/fixtures/report-profile-violation-stage2.json");

        assertThatThrownBy(() -> validator.validate(report))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.PROFILE_VIOLATION));
    }

    @Test
    void rejectsStage3ReportWithMissingLabels() throws Exception {
        TelemetryReport report = loadReport("/fixtures/report-profile-violation-stage3.json");

        assertThatThrownBy(() -> validator.validate(report))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.PROFILE_VIOLATION));
    }

    @Test
    void rejectsReportWithUnknownMeasurementKey() {
        TelemetryReport report = new TelemetryReport(
                1,
                "0xbd23cd342e8d6764ba34c70b9da72967ec708e76f1acf807f196c4557b5e635b",
                0,
                "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
                1800000000L,
                java.util.Map.of(
                        "checklistItemsTotal", 12L,
                        "checklistItemsPassed", 12L,
                        "extraField", 1L),
                true);

        assertThatThrownBy(() -> validator.validate(report))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.PROFILE_VIOLATION));
    }

    @Test
    void rejectsReportWithValueOutsideRange() {
        TelemetryReport report = new TelemetryReport(
                1,
                "0xbd23cd342e8d6764ba34c70b9da72967ec708e76f1acf807f196c4557b5e635b",
                0,
                "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
                1800000000L,
                java.util.Map.of(
                        "checklistItemsTotal", 12L,
                        "checklistItemsPassed", 200L),
                true);

        assertThatThrownBy(() -> validator.validate(report))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.PROFILE_VIOLATION));
    }

    private TelemetryReport loadReport(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing fixture " + resource);
            }
            JsonNode root = mapper.readTree(input);
            return mapper.treeToValue(root.get("report"), TelemetryReport.class);
        }
    }
}
