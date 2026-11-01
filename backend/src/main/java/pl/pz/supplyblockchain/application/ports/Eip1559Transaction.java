package pl.pz.supplyblockchain.application.ports;

import java.math.BigInteger;

public record Eip1559Transaction(
        long chainId,
        BigInteger nonce,
        BigInteger gasLimit,
        BigInteger maxPriorityFeePerGas,
        BigInteger maxFeePerGas,
        String to,
        BigInteger value,
        String data) {
}
