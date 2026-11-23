package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.BatchState;
import pl.pz.supplyblockchain.application.ports.ChainReceipt;
import pl.pz.supplyblockchain.application.ports.ChainUnavailableException;
import pl.pz.supplyblockchain.application.ports.RelayerChainGateway;
import pl.pz.supplyblockchain.application.ports.RelayerTransactionException;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.TransactionReceiptStatus;

/**
 * Low-level relayer RPC. No generated contract wrapper, no {@code .send()} wrapper and no gas provider:
 * the sender signs and broadcasts raw EIP-1559 bytes itself.
 */
@Component
public class Web3jRelayerChainGateway implements RelayerChainGateway {

    private static final Pattern GET_BATCH_RESULT = Pattern.compile("^0x[0-9a-fA-F]{192}$");
    private static final BigInteger MAX_UINT32 = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);

    private final Web3j web3j;
    private final String contractAddress;

    public Web3jRelayerChainGateway(Web3j web3j, ChainProperties chainProperties) {
        this.web3j = web3j;
        this.contractAddress = chainProperties.contractAddress();
    }

    @Override
    public BigInteger pendingNonce(String address) {
        try {
            EthGetTransactionCount response =
                    web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).send();
            if (response == null) {
                throw unavailable("pending nonce returned no response", null);
            }
            if (response.hasError()) {
                throw unavailable("pending nonce RPC error: " + response.getError().getMessage(), null);
            }
            BigInteger nonce = response.getTransactionCount();
            if (nonce == null || nonce.signum() < 0) {
                throw unavailable("pending nonce returned no nonce", null);
            }
            return nonce;
        } catch (IOException exception) {
            throw unavailable("pending nonce could not be read", exception);
        }
    }

    @Override
    public BigInteger maxPriorityFeePerGas() {
        try {
            EthMaxPriorityFeePerGas response = web3j.ethMaxPriorityFeePerGas().send();
            if (response == null) {
                throw unavailable("max priority fee returned no response", null);
            }
            if (response.hasError()) {
                throw unavailable("max priority fee RPC error: " + response.getError().getMessage(), null);
            }
            BigInteger priorityFee = response.getMaxPriorityFeePerGas();
            if (priorityFee == null || priorityFee.signum() < 0) {
                throw unavailable("max priority fee returned no value", null);
            }
            return priorityFee;
        } catch (IOException exception) {
            throw unavailable("max priority fee could not be read", exception);
        }
    }

    @Override
    public BigInteger latestBaseFeePerGas() {
        try {
            EthBlock response =
                    web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
            if (response == null) {
                throw unavailable("latest block returned no response", null);
            }
            if (response.hasError()) {
                throw unavailable("latest block RPC error: " + response.getError().getMessage(), null);
            }
            EthBlock.Block block = response.getBlock();
            if (block == null || block.getBaseFeePerGas() == null) {
                throw unavailable("latest block has no base fee", null);
            }
            return block.getBaseFeePerGas();
        } catch (IOException exception) {
            throw unavailable("latest base fee could not be read", exception);
        }
    }

    @Override
    public BigInteger estimateGas(String from, String to, String data) {
        try {
            EthEstimateGas response = web3j.ethEstimateGas(
                    Transaction.createEthCallTransaction(from, to, data)).send();
            if (response == null) {
                throw unavailable("gas estimate returned no response", null);
            }
            if (response.hasError()) {
                throw estimateFailure(response.getError().getMessage());
            }
            BigInteger amount = response.getAmountUsed();
            if (amount == null || amount.signum() <= 0) {
                throw unavailable("gas estimate returned no amount", null);
            }
            return amount;
        } catch (IOException exception) {
            throw unavailable("gas estimate could not be read", exception);
        }
    }

    @Override
    public String sendRawTransaction(String rawTransaction) {
        try {
            EthSendTransaction response = web3j.ethSendRawTransaction(rawTransaction).send();
            if (response == null) {
                throw unavailable("raw transaction returned no response", null);
            }
            if (response.hasError()) {
                throw sendFailure(response.getError().getMessage());
            }
            String hash = response.getTransactionHash();
            if (hash == null || hash.isBlank()) {
                throw unavailable("raw transaction returned no hash", null);
            }
            return hash;
        } catch (IOException exception) {
            throw unavailable("raw transaction could not be sent", exception);
        }
    }

    @Override
    public Optional<ChainReceipt> findReceipt(String txHash) {
        try {
            EthGetTransactionReceipt response = web3j.ethGetTransactionReceipt(txHash).send();
            if (response == null) {
                throw unavailable("receipt returned no response", null);
            }
            if (response.hasError()) {
                throw unavailable("receipt RPC error: " + response.getError().getMessage(), null);
            }
            Optional<TransactionReceipt> receipt = response.getTransactionReceipt();
            if (receipt.isEmpty()) {
                return Optional.empty();
            }
            TransactionReceipt value = receipt.get();
            TransactionReceiptStatus status = receiptStatus(value.getStatus());
            if (value.getBlockNumber() == null || value.getBlockNumber().signum() < 0
                    || value.getBlockHash() == null) {
                throw unavailable("receipt has no block position", null);
            }
            return Optional.of(new ChainReceipt(
                    status, value.getBlockNumber().longValueExact(), value.getBlockHash()));
        } catch (IOException | ArithmeticException exception) {
            throw unavailable("receipt could not be read", exception);
        }
    }

    @Override
    public BatchState getBatch(String batchId) {
        String data = FunctionEncoder.encode(new Function(
                "getBatch",
                List.of(new Bytes32(Numeric.hexStringToByteArray(batchId))),
                List.of()));
        try {
            EthCall response = web3j.ethCall(
                            Transaction.createEthCallTransaction(null, contractAddress, data),
                            DefaultBlockParameterName.LATEST)
                    .send();
            if (response == null) {
                throw unavailable("getBatch returned no response", null);
            }
            if (response.hasError()) {
                throw unavailable("getBatch RPC error: " + response.getError().getMessage(), null);
            }
            return decodeBatch(response.getValue());
        } catch (IOException exception) {
            throw unavailable("getBatch could not be read", exception);
        }
    }

    static BatchState decodeBatch(String resultHex) {
        if (resultHex == null || !GET_BATCH_RESULT.matcher(resultHex).matches()) {
            throw unavailable("getBatch returned an invalid ABI result", null);
        }
        byte[] result = Numeric.hexStringToByteArray(resultHex);
        BigInteger exists = word(result, 0);
        BigInteger nextStage = word(result, 1);
        BigInteger completed = word(result, 2);
        if (exists.compareTo(BigInteger.ONE) > 0
                || completed.compareTo(BigInteger.ONE) > 0
                || nextStage.compareTo(MAX_UINT32) > 0) {
            throw unavailable("getBatch returned non-canonical booleans", null);
        }
        return new BatchState(
                exists.signum() == 1, nextStage.intValueExact(), completed.signum() == 1);
    }

    private static TransactionReceiptStatus receiptStatus(String status) {
        if ("0x1".equalsIgnoreCase(status)) {
            return TransactionReceiptStatus.SUCCESS;
        }
        if ("0x0".equalsIgnoreCase(status)) {
            return TransactionReceiptStatus.REVERTED;
        }
        throw unavailable("receipt has an unknown status", null);
    }

    private static BigInteger word(byte[] data, int index) {
        return new BigInteger(1, java.util.Arrays.copyOfRange(data, index * 32, (index + 1) * 32));
    }

    private static RelayerTransactionException estimateFailure(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lower.contains("execution reverted") || lower.contains("revert")) {
            return new RelayerTransactionException(
                    false, "GAS_ESTIMATION_REVERTED", "gas estimation reverted", null);
        }
        return new RelayerTransactionException(
                true, "GAS_ESTIMATION_UNAVAILABLE", "gas estimation failed", null);
    }

    private static RelayerTransactionException sendFailure(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lower.contains("insufficient funds")
                || lower.contains("intrinsic gas")
                || lower.contains("invalid sender")) {
            return new RelayerTransactionException(
                    false, "SEND_REJECTED", "raw transaction was rejected", null);
        }
        return new RelayerTransactionException(
                true, "SEND_UNCERTAIN", "raw transaction outcome is unknown", null);
    }

    private static <T> T read(T response) {
        if (response == null) {
            throw unavailable("RPC returned no response", null);
        }
        return response;
    }

    private static ChainUnavailableException unavailable(String message, Throwable cause) {
        return new ChainUnavailableException(message, cause);
    }
}
