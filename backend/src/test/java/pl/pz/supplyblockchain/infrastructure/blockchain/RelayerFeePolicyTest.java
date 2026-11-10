package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class RelayerFeePolicyTest {

    @Test
    void usesRemoteTipWhenItIsAboveTheFloor() {
        BigInteger remote = BigInteger.valueOf(45_000_000_000L);

        assertThat(RelayerFeePolicy.effectivePriorityFee(remote, 30)).isEqualTo(remote);
    }

    @Test
    void raisesTipToConfiguredMinimum() {
        BigInteger remote = BigInteger.valueOf(1_000_000_000L);

        assertThat(RelayerFeePolicy.effectivePriorityFee(remote, 30))
                .isEqualTo(BigInteger.valueOf(30_000_000_000L));
    }

    @Test
    void handlesMissingRemoteTip() {
        assertThat(RelayerFeePolicy.effectivePriorityFee(null, 30))
                .isEqualTo(BigInteger.valueOf(30_000_000_000L));
    }

    @Test
    void maxFeeAddsTwoBaseFeesAndTheTip() {
        assertThat(RelayerFeePolicy.maxFee(BigInteger.valueOf(100), BigInteger.valueOf(30)))
                .isEqualTo(BigInteger.valueOf(230));
    }
}
