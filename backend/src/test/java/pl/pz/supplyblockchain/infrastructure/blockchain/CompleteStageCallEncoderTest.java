package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.CompleteStageCall;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ABI compatibility with Solidity: {@code fixtures/complete-stage-calldata.json} (copy of
 * {@code shared/fixtures}) contains calldata encoded independently by {@code cast calldata}; the
 * Foundry test {@code CompleteStageCalldata.t.sol} checks the same bytes against {@code abi.encodeCall}.
 */
class CompleteStageCallEncoderTest {

    private static final String FUNCTION_SIGNATURE =
            "completeStage(bytes32,uint32,bytes32,bool,uint256,string,bytes,uint256,bytes)";
    private static final String BATCH_ID =
            "0x79709e7401c7c94e8c6439dd2cd3729509c3c33d6b80b2df940e9c435df582f1";
    private static final String REPORT_HASH =
            "0x388322c4e5f9429a457ab80640548381b4cb000d14e933561f40db7f2b017ffe";

    private final CompleteStageCallEncoder encoder = new CompleteStageCallEncoder();

    static List<Arguments> fixtureCases() throws IOException {
        JsonNode cases = loadFixture().get("cases");
        List<Arguments> arguments = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            JsonNode testCase = cases.get(i);
            JsonNode input = testCase.get("input");
            CompleteStageCall call = new CompleteStageCall(
                    input.get("batchId").asString(),
                    input.get("stageId").asInt(),
                    input.get("reportHash").asString(),
                    input.get("qaPassed").asBoolean(),
                    input.get("deadline").asLong(),
                    input.get("ipfsCid").asString(),
                    input.get("stationSignature").asString(),
                    input.get("approvalDeadline").asLong(),
                    input.get("approvalSignature").asString());
            arguments.add(Arguments.of(
                    testCase.get("name").asString(), call, testCase.get("expectedCalldata").asString()));
        }
        return arguments;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureCases")
    void encodesBytesIdenticalToSolidity(String name, CompleteStageCall call, String expectedCalldata) {
        assertThat(encoder.encode(call)).isEqualTo(expectedCalldata);
    }

    @Test
    void fixtureCoversAllCasesAndTheContractSelector() throws IOException {
        JsonNode fixture = loadFixture();

        assertThat(fixture.get("function").asString()).isEqualTo(FUNCTION_SIGNATURE);
        assertThat(fixture.get("selector").asString()).isEqualTo(selector());
        assertThat(fixtureCases()).hasSize(5);
    }

    @Test
    void treatsMissingApprovalLikeEmptyBytes() {
        String empty = encoder.encode(call("0x"));

        assertThat(encoder.encode(call(null))).isEqualTo(empty);
        assertThat(encoder.encode(call(""))).isEqualTo(empty);
    }

    @Test
    void rejectsApprovalSignatureWithWrongLength() {
        assertThatThrownBy(() -> encoder.encode(call("0x" + "22".repeat(64))))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SCHEMA_VIOLATION))
                .hasMessageContaining("approvalSignature must be 65 bytes");
    }

    @Test
    void requiresStationSignature() {
        CompleteStageCall missing = new CompleteStageCall(
                BATCH_ID, 0, REPORT_HASH, true, 1800000600, "bafyexample", "0x", 0, "0x");

        assertThatThrownBy(() -> encoder.encode(missing))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("stationSignature must be 65 bytes");
    }

    private static CompleteStageCall call(String approvalSignature) {
        return new CompleteStageCall(
                BATCH_ID, 0, REPORT_HASH, true, 1800000600, "bafyexample",
                "0x" + "11".repeat(65), 0, approvalSignature);
    }

    private static String selector() {
        return Numeric.toHexString(Hash.sha3(FUNCTION_SIGNATURE.getBytes(StandardCharsets.UTF_8)))
                .substring(0, 10);
    }

    private static JsonNode loadFixture() throws IOException {
        try (InputStream input = CompleteStageCallEncoderTest.class.getResourceAsStream(
                "/fixtures/complete-stage-calldata.json")) {
            if (input == null) {
                throw new IllegalStateException("complete-stage-calldata fixture is missing");
            }
            return JsonMapper.builder().build().readTree(input);
        }
    }
}
