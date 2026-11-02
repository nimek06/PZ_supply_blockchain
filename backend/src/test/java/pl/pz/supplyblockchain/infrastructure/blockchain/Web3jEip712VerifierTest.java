package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.SignatureVerification;
import pl.pz.supplyblockchain.domain.StageApprovalMessage;
import pl.pz.supplyblockchain.domain.StageCompletionMessage;
import pl.pz.supplyblockchain.support.SignatureVectorFixture;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class Web3jEip712VerifierTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Web3jEip712Verifier verifier = new Web3jEip712Verifier(new ChainProperties(
            31337,
            "0x5fbdb2315678afecb367f032d93f642f64180aa3",
            "SupplyChain",
            "1",
            3600));

    @Test
    void verifiesStageCompletionVector() throws Exception {
        SignatureVectorFixture vector = SignatureVectorFixture.load(mapper);
        SignatureVectorFixture.StageCompletion stage = vector.stageCompletion();
        JsonNode message = stage.message();

        SignatureVerification result = verifier.verifyCompletion(
                new StageCompletionMessage(
                        message.get("batchId").asString(),
                        message.get("stageId").asInt(),
                        message.get("reportHash").asString(),
                        message.get("qaPassed").asBoolean(),
                        message.get("deadline").asLong()),
                stage.signature());

        assertThat(result.digest()).isEqualTo(stage.digest());
        assertThat(result.signerAddress()).isEqualTo(stage.signer().toLowerCase());
    }

    @Test
    void verifiesStageApprovalVector() throws Exception {
        SignatureVectorFixture vector = SignatureVectorFixture.load(mapper);
        SignatureVectorFixture.StageApproval stage = vector.stageApproval();
        JsonNode message = stage.message();

        SignatureVerification result = verifier.verifyApproval(
                new StageApprovalMessage(
                        message.get("batchId").asString(),
                        message.get("stageId").asInt(),
                        message.get("reportHash").asString(),
                        message.get("deadline").asLong()),
                stage.signature());

        assertThat(result.digest()).isEqualTo(stage.digest());
        assertThat(result.signerAddress()).isEqualTo(stage.signer().toLowerCase());
    }

    @Test
    void changedSignedValueRecoversDifferentSigner() throws Exception {
        SignatureVectorFixture vector = SignatureVectorFixture.load(mapper);
        SignatureVectorFixture.StageCompletion stage = vector.stageCompletion();
        JsonNode message = stage.message();

        SignatureVerification changed = verifier.verifyCompletion(
                new StageCompletionMessage(
                        message.get("batchId").asString(),
                        message.get("stageId").asInt(),
                        message.get("reportHash").asString(),
                        !message.get("qaPassed").asBoolean(),
                        message.get("deadline").asLong()),
                stage.signature());

        assertThat(changed.signerAddress()).isNotEqualTo(stage.signer().toLowerCase());
    }

    @Test
    void rejectsMalformedSignature() throws Exception {
        SignatureVectorFixture vector = SignatureVectorFixture.load(mapper);
        SignatureVectorFixture.StageCompletion stage = vector.stageCompletion();
        JsonNode message = stage.message();

        assertThatThrownBy(() -> verifier.verifyCompletion(
                new StageCompletionMessage(
                        message.get("batchId").asString(),
                        message.get("stageId").asInt(),
                        message.get("reportHash").asString(),
                        message.get("qaPassed").asBoolean(),
                        message.get("deadline").asLong()),
                "0x1234"))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).code().name())
                        .isEqualTo("INVALID_SIGNATURE"));
    }
}
