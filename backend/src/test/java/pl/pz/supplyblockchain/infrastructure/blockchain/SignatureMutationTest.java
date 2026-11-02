package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.SignatureVerification;
import pl.pz.supplyblockchain.domain.StageApprovalMessage;
import pl.pz.supplyblockchain.domain.StageCompletionMessage;
import pl.pz.supplyblockchain.domain.TelemetryReport;
import pl.pz.supplyblockchain.infrastructure.json.JcsReportCanonicalizer;
import pl.pz.supplyblockchain.support.SignatureVectorFixture;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mutation tests for the shared signature vector: every signed or hashed field must be protected,
 * so changing any single value has to change the report hash or the recovered signer.
 */
class SignatureMutationTest {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String OTHER_BYTES32 =
            "0x0000000000000000000000000000000000000000000000000000000000000001";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final JcsReportCanonicalizer CANONICALIZER = new JcsReportCanonicalizer(MAPPER);
    private static final Web3jEip712Verifier VERIFIER = verifier(31337, CONTRACT);

    private static SignatureVectorFixture vector;
    private static TelemetryReport report;
    private static StageCompletionMessage completion;
    private static StageApprovalMessage approval;

    @BeforeAll
    static void loadVector() throws Exception {
        vector = SignatureVectorFixture.load(MAPPER);
        report = vector.telemetryReport(MAPPER);
        var c = vector.stageCompletion().message();
        completion = new StageCompletionMessage(
                c.get("batchId").asString(),
                c.get("stageId").asInt(),
                c.get("reportHash").asString(),
                c.get("qaPassed").asBoolean(),
                c.get("deadline").asLong());
        var a = vector.stageApproval().message();
        approval = new StageApprovalMessage(
                a.get("batchId").asString(),
                a.get("stageId").asInt(),
                a.get("reportHash").asString(),
                a.get("deadline").asLong());
    }

    // --- report hash ---------------------------------------------------------------------------

    // schemaVersion is a constant (1), so it is covered by reportRejectsOtherSchemaVersionAndUppercaseAddress.
    static Stream<Arguments> reportMutations() {
        return Stream.of(
                mutation("batchId", r -> withReport(r, OTHER_BYTES32, r.stageId(), r.stationAddress(),
                        r.timestamp(), r.measurements(), r.qaPassed())),
                mutation("stageId", r -> withReport(r, r.batchId(), r.stageId() + 1, r.stationAddress(),
                        r.timestamp(), r.measurements(), r.qaPassed())),
                mutation("stationAddress", r -> withReport(r, r.batchId(), r.stageId(),
                        "0x3c44cdddb6a900fa2b585dd299e03d12fa4293bc", r.timestamp(), r.measurements(),
                        r.qaPassed())),
                mutation("timestamp", r -> withReport(r, r.batchId(), r.stageId(), r.stationAddress(),
                        r.timestamp() + 1, r.measurements(), r.qaPassed())),
                mutation("qaPassed", r -> withReport(r, r.batchId(), r.stageId(), r.stationAddress(),
                        r.timestamp(), r.measurements(), !r.qaPassed())),
                mutation("measurements: extra key", r -> withMeasurements(r, m -> m.put("extraUnit", 0L))),
                mutation("measurements: removed key", r -> withMeasurements(r,
                        m -> m.remove(m.keySet().iterator().next()))),
                mutation("measurements: renamed key", r -> withMeasurements(r, m -> {
                    String key = m.keySet().iterator().next();
                    Long value = m.remove(key);
                    m.put(key + "X", value);
                })));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reportMutations")
    void changingAnyReportFieldChangesReportHash(String field, UnaryOperator<TelemetryReport> mutate) {
        String mutatedHash = CANONICALIZER.canonicalize(mutate.apply(report)).reportHash();

        assertThat(mutatedHash).as(field).isNotEqualTo(vector.reportHash());
    }

    @Test
    void changingEachMeasurementValueChangesReportHash() {
        for (String key : report.measurements().keySet()) {
            TelemetryReport mutated = withMeasurements(report, m -> m.put(key, m.get(key) + 1));

            assertThat(CANONICALIZER.canonicalize(mutated).reportHash())
                    .as("measurements." + key)
                    .isNotEqualTo(vector.reportHash());
        }
    }

    @Test
    void measurementOrderDoesNotChangeReportHash() {
        Map<String, Long> reversed = new LinkedHashMap<>();
        List<String> keys = List.copyOf(report.measurements().keySet());
        for (int i = keys.size() - 1; i >= 0; i--) {
            reversed.put(keys.get(i), report.measurements().get(keys.get(i)));
        }
        TelemetryReport reordered = withReport(report, report.batchId(), report.stageId(),
                report.stationAddress(), report.timestamp(), reversed, report.qaPassed());

        assertThat(CANONICALIZER.canonicalize(reordered).reportHash()).isEqualTo(vector.reportHash());
    }

    @Test
    void reportRejectsOtherSchemaVersionAndUppercaseAddress() {
        assertThatThrownBy(() -> new TelemetryReport(2, report.batchId(), report.stageId(),
                report.stationAddress(), report.timestamp(), report.measurements(), report.qaPassed()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new TelemetryReport(1, report.batchId(), report.stageId(),
                report.stationAddress().toUpperCase(Locale.ROOT).replace("0X", "0x"),
                report.timestamp(), report.measurements(), report.qaPassed()))
                .isInstanceOf(DomainException.class);
    }

    // --- StageCompletion -----------------------------------------------------------------------

    static Stream<Arguments> completionMutations() {
        return Stream.of(
                Arguments.of("batchId", (UnaryOperator<StageCompletionMessage>) m -> new StageCompletionMessage(
                        OTHER_BYTES32, m.stageId(), m.reportHash(), m.qaPassed(), m.deadline())),
                Arguments.of("stageId", (UnaryOperator<StageCompletionMessage>) m -> new StageCompletionMessage(
                        m.batchId(), m.stageId() + 1, m.reportHash(), m.qaPassed(), m.deadline())),
                Arguments.of("reportHash", (UnaryOperator<StageCompletionMessage>) m -> new StageCompletionMessage(
                        m.batchId(), m.stageId(), OTHER_BYTES32, m.qaPassed(), m.deadline())),
                Arguments.of("qaPassed", (UnaryOperator<StageCompletionMessage>) m -> new StageCompletionMessage(
                        m.batchId(), m.stageId(), m.reportHash(), !m.qaPassed(), m.deadline())),
                Arguments.of("deadline", (UnaryOperator<StageCompletionMessage>) m -> new StageCompletionMessage(
                        m.batchId(), m.stageId(), m.reportHash(), m.qaPassed(), m.deadline() + 1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("completionMutations")
    void changingAnyCompletionFieldBreaksStationSignature(
            String field, UnaryOperator<StageCompletionMessage> mutate) {
        SignatureVerification result = VERIFIER.verifyCompletion(
                mutate.apply(completion), vector.stageCompletion().signature());

        assertThat(result.digest()).as(field).isNotEqualTo(vector.stageCompletion().digest());
        assertThat(result.signerAddress()).as(field).isNotEqualTo(lower(vector.stageCompletion().signer()));
    }

    // --- StageApproval -------------------------------------------------------------------------

    static Stream<Arguments> approvalMutations() {
        return Stream.of(
                Arguments.of("batchId", (UnaryOperator<StageApprovalMessage>) m -> new StageApprovalMessage(
                        OTHER_BYTES32, m.stageId(), m.reportHash(), m.deadline())),
                Arguments.of("stageId", (UnaryOperator<StageApprovalMessage>) m -> new StageApprovalMessage(
                        m.batchId(), m.stageId() + 1, m.reportHash(), m.deadline())),
                Arguments.of("reportHash", (UnaryOperator<StageApprovalMessage>) m -> new StageApprovalMessage(
                        m.batchId(), m.stageId(), OTHER_BYTES32, m.deadline())),
                Arguments.of("deadline", (UnaryOperator<StageApprovalMessage>) m -> new StageApprovalMessage(
                        m.batchId(), m.stageId(), m.reportHash(), m.deadline() + 1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("approvalMutations")
    void changingAnyApprovalFieldBreaksOperatorSignature(
            String field, UnaryOperator<StageApprovalMessage> mutate) {
        SignatureVerification result = VERIFIER.verifyApproval(
                mutate.apply(approval), vector.stageApproval().signature());

        assertThat(result.digest()).as(field).isNotEqualTo(vector.stageApproval().digest());
        assertThat(result.signerAddress()).as(field).isNotEqualTo(lower(vector.stageApproval().signer()));
    }

    // --- domain and type separation ------------------------------------------------------------

    @Test
    void otherChainOrContractBreaksBothSignatures() {
        for (Web3jEip712Verifier other : List.of(
                verifier(80002, CONTRACT),
                verifier(31337, "0xe7f1725e7734ce288f8367e1bb143e90bb3f0512"))) {
            assertThat(other.verifyCompletion(completion, vector.stageCompletion().signature()).signerAddress())
                    .isNotEqualTo(lower(vector.stageCompletion().signer()));
            assertThat(other.verifyApproval(approval, vector.stageApproval().signature()).signerAddress())
                    .isNotEqualTo(lower(vector.stageApproval().signer()));
        }
    }

    @Test
    void stationSignatureIsNotAcceptedAsApproval() {
        SignatureVerification result = VERIFIER.verifyApproval(approval, vector.stageCompletion().signature());

        assertThat(result.signerAddress()).isNotEqualTo(lower(vector.stageCompletion().signer()));
    }

    // --- address case ---------------------------------------------------------------------------

    @Test
    void contractAddressCaseDoesNotChangeResult() {
        String checksummed = "0x5FbDB2315678afecb367f032d93F642f64180aa3";
        for (String address : List.of(CONTRACT, checksummed, "0x" + CONTRACT.substring(2).toUpperCase(Locale.ROOT))) {
            Web3jEip712Verifier caseVariant = verifier(31337, address);

            SignatureVerification c = caseVariant.verifyCompletion(completion, vector.stageCompletion().signature());
            SignatureVerification a = caseVariant.verifyApproval(approval, vector.stageApproval().signature());

            assertThat(c.digest()).as(address).isEqualTo(vector.stageCompletion().digest());
            assertThat(c.signerAddress()).as(address).isEqualTo(lower(vector.stageCompletion().signer()));
            assertThat(a.digest()).as(address).isEqualTo(vector.stageApproval().digest());
            assertThat(a.signerAddress()).as(address).isEqualTo(lower(vector.stageApproval().signer()));
        }
    }

    @Test
    void hexCaseOfSignedBytes32DoesNotChangeResult() {
        StageCompletionMessage upper = new StageCompletionMessage(
                upperHex(completion.batchId()), completion.stageId(), upperHex(completion.reportHash()),
                completion.qaPassed(), completion.deadline());

        SignatureVerification result = VERIFIER.verifyCompletion(upper, vector.stageCompletion().signature());

        assertThat(result.digest()).isEqualTo(vector.stageCompletion().digest());
        assertThat(result.signerAddress()).isEqualTo(lower(vector.stageCompletion().signer()));
    }

    @Test
    void recoveredSignerIsAlwaysLowercase() {
        String signer = VERIFIER.verifyCompletion(completion, vector.stageCompletion().signature()).signerAddress();

        assertThat(signer).matches("^0x[0-9a-f]{40}$");
    }

    // --- helpers --------------------------------------------------------------------------------

    private static Arguments mutation(String name, UnaryOperator<TelemetryReport> mutate) {
        return Arguments.of(name, mutate);
    }

    private static TelemetryReport withReport(TelemetryReport base, String batchId, int stageId,
            String stationAddress, long timestamp, Map<String, Long> measurements, boolean qaPassed) {
        return new TelemetryReport(base.schemaVersion(), batchId, stageId, stationAddress, timestamp,
                measurements, qaPassed);
    }

    private static TelemetryReport withMeasurements(TelemetryReport base,
            java.util.function.Consumer<Map<String, Long>> change) {
        Map<String, Long> copy = new LinkedHashMap<>(base.measurements());
        change.accept(copy);
        return withReport(base, base.batchId(), base.stageId(), base.stationAddress(), base.timestamp(),
                copy, base.qaPassed());
    }

    private static Web3jEip712Verifier verifier(long chainId, String contract) {
        return new Web3jEip712Verifier(new ChainProperties(chainId, contract, "SupplyChain", "1", 3600));
    }

    private static String lower(String address) {
        return address.toLowerCase(Locale.ROOT);
    }

    private static String upperHex(String hex) {
        return "0x" + hex.substring(2).toUpperCase(Locale.ROOT);
    }
}
