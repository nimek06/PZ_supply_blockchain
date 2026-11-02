package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.CompleteStageCall;

class CompleteStageCallEncoderTest {

    private final CompleteStageCallEncoder encoder = new CompleteStageCallEncoder();

    @Test
    void encodesCompleteStageSelectorAndDynamicArguments() {
        CompleteStageCall call = new CompleteStageCall(
                "0x79709e7401c7c94e8c6439dd2cd3729509c3c33d6b80b2df940e9c435df582f1",
                2,
                "0x4707421ac9d17015745b8d29d2684f271eb6d37cc2a22f7f01456b88a894e97d",
                true,
                1800000600,
                "bafyexample",
                "0x" + "11".repeat(65),
                1800000900,
                "0x" + "22".repeat(65));

        String encoded = encoder.encode(call);
        String signature = "completeStage(bytes32,uint32,bytes32,bool,uint256,string,bytes,uint256,bytes)";
        String selector = Numeric.toHexString(
                Hash.sha3(signature.getBytes(StandardCharsets.UTF_8))).substring(0, 10);

        assertThat(encoded).startsWith(selector);
        assertThat(encoded.length()).isGreaterThan(10 + 64 * 5);
    }
}
