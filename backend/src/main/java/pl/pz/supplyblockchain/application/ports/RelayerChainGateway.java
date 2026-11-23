package pl.pz.supplyblockchain.application.ports;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Low-level chain access for the relayer. Implementations own Web3j; callers see domain values only.
 * Transport and malformed-response failures are never reported as a successful chain result.
 */
public interface RelayerChainGateway {

    BigInteger pendingNonce(String address);

    BigInteger maxPriorityFeePerGas();

    BigInteger latestBaseFeePerGas();

    BigInteger estimateGas(String from, String to, String data);

    String sendRawTransaction(String rawTransaction);

    Optional<ChainReceipt> findReceipt(String txHash);

    BatchState getBatch(String batchId);
}
