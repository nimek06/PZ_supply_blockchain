package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

class CreateBatchCallEncoderTest {

    private final CreateBatchCallEncoder encoder = new CreateBatchCallEncoder();

    @Test
    void encodesCreateBatchWithTheAbiSelector() {
        String data = encoder.encode("B-2026-0001");

        byte[] bytes = Numeric.hexStringToByteArray(data);
        String expectedSelector = Hash.sha3String("createBatch(string)").substring(0, 10);
        assertThat(Numeric.toHexStringNoPrefix(java.util.Arrays.copyOf(bytes, 4)))
                .isEqualTo(expectedSelector.substring(2));
        assertThat(data).startsWith(expectedSelector);
    }

    @Test
    void rejectsBlankBatchNumber() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> encoder.encode(" "))
                .isInstanceOf(pl.pz.supplyblockchain.domain.DomainException.class);
    }
}
