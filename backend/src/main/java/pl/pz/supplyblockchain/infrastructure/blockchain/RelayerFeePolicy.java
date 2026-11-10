package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.math.BigInteger;

/**
 * EIP-1559 fee calculation with a configurable minimum priority fee.
 */
public final class RelayerFeePolicy {

    private static final BigInteger WEI_PER_GWEI = BigInteger.TEN.pow(9);

    private RelayerFeePolicy() {
    }

    public static BigInteger effectivePriorityFee(BigInteger rpcPriorityFee, long minTipGwei) {
        BigInteger minimum = BigInteger.valueOf(minTipGwei).multiply(WEI_PER_GWEI);
        if (rpcPriorityFee == null || rpcPriorityFee.signum() < 0) {
            return minimum;
        }
        return rpcPriorityFee.max(minimum);
    }

    public static BigInteger maxFee(BigInteger baseFee, BigInteger priorityFee) {
        if (baseFee == null || baseFee.signum() < 0) {
            throw new IllegalArgumentException("baseFee must not be negative");
        }
        return baseFee.multiply(BigInteger.TWO).add(priorityFee);
    }
}
