package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import pl.pz.supplyblockchain.application.ports.CompleteStageCall;
import pl.pz.supplyblockchain.application.ports.Eip1559Transaction;
import pl.pz.supplyblockchain.application.ports.SignedTransaction;
import pl.pz.supplyblockchain.config.RelayerProperties;

/**
 * Explicit spike for the future relayer. It uses low-level Web3j calls only; no generated
 * contract wrapper is involved. The class is intentionally not a Spring bean yet.
 */
public class Web3jRelayerSpike {

    private final Web3j web3j;
    private final Credentials credentials;
    private final long chainId;
    private final String contractAddress;
    private final CompleteStageCallEncoder encoder;
    private final Eip1559TransactionSigner signer;
    private final RelayerProperties relayerProperties;

    public Web3jRelayerSpike(
            Web3j web3j,
            Credentials credentials,
            long chainId,
            String contractAddress,
            CompleteStageCallEncoder encoder,
            Eip1559TransactionSigner signer,
            RelayerProperties relayerProperties) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.chainId = chainId;
        this.contractAddress = contractAddress;
        this.encoder = encoder;
        this.signer = signer;
        this.relayerProperties = relayerProperties;
    }

    public SignedTransaction signCompleteStage(CompleteStageCall call) throws IOException {
        String from = credentials.getAddress();
        String data = encoder.encode(call);

        BigInteger nonce = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.PENDING)
                .send()
                .getTransactionCount();
        BigInteger priorityFee = RelayerFeePolicy.effectivePriorityFee(
                web3j.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas(),
                relayerProperties.minTipGwei());
        BigInteger baseFee = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                .send()
                .getBlock()
                .getBaseFeePerGas();
        BigInteger maxFee = RelayerFeePolicy.maxFee(baseFee, priorityFee);
        BigInteger estimated = web3j.ethEstimateGas(
                        Transaction.createEthCallTransaction(from, contractAddress, data))
                .send()
                .getAmountUsed();
        BigInteger gasLimit = estimated.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));

        return signer.sign(new Eip1559Transaction(
                chainId,
                nonce,
                gasLimit,
                priorityFee,
                maxFee,
                contractAddress,
                BigInteger.ZERO,
                data), credentials);
    }

    public String submit(SignedTransaction transaction) throws IOException {
        return web3j.ethSendRawTransaction(transaction.rawTransaction())
                .send()
                .getTransactionHash();
    }
}
