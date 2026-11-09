package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.StationRegistry;
import pl.pz.supplyblockchain.application.ports.StationRegistryException;
import pl.pz.supplyblockchain.application.ports.StationRegistryException.Reason;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StationRegistration;

/**
 * {@link StationRegistry} over {@code eth_call SupplyChain.getStation(address)} at the {@code latest} block.
 * A registration seen at {@code latest} can still be reorged away; callers re-check before each send.
 * Timeouts belong to the injected {@link Web3j} transport. Low-level Web3j only, no generated wrapper;
 * not a Spring bean until the chain client is configured.
 */
public class Web3jStationRegistry implements StationRegistry {

    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    /** {@code (bool active, bytes32 stationId, uint64 expiresAt, uint32 stageMask)}: four static words. */
    private static final Pattern RESULT = Pattern.compile("^0x[0-9a-fA-F]{256}$");
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private final Web3j web3j;
    private final String contractAddress;

    public Web3jStationRegistry(Web3j web3j, DeploymentId deployment) {
        this.web3j = Objects.requireNonNull(web3j, "web3j");
        this.contractAddress = Objects.requireNonNull(deployment, "deployment").contractAddress();
    }

    @Override
    public StationRegistration getStation(String stationAddress) {
        String data = encodeGetStation(stationAddress);
        EthCall response;
        try {
            response = web3j.ethCall(
                            Transaction.createEthCallTransaction(null, contractAddress, data),
                            DefaultBlockParameterName.LATEST)
                    .send();
        } catch (IOException | RuntimeException exception) {
            throw new StationRegistryException(Reason.UNAVAILABLE, "getStation RPC call failed", exception);
        }
        if (response == null) {
            throw new StationRegistryException(Reason.UNAVAILABLE, "getStation RPC returned no response", null);
        }
        if (response.hasError()) {
            throw new StationRegistryException(Reason.UNAVAILABLE,
                    "getStation RPC error " + response.getError().getCode() + ": " + response.getError().getMessage(),
                    null);
        }
        return decodeGetStation(response.getValue());
    }

    static String encodeGetStation(String stationAddress) {
        if (stationAddress == null || !ADDRESS.matcher(stationAddress).matches()) {
            throw new IllegalArgumentException("station address must be a 20-byte hex address");
        }
        return FunctionEncoder.encode(new Function(
                "getStation", List.of(new Address(stationAddress.toLowerCase(Locale.ROOT))), List.of()));
    }

    /**
     * Strict ABI decoding: unlike Web3j's return decoder it rejects non-zero padding and a bool other
     * than 0/1, so a broken node or a wrong contract address cannot be read as a registration.
     */
    static StationRegistration decodeGetStation(String resultHex) {
        if (resultHex == null || !RESULT.matcher(resultHex).matches()) {
            throw new StationRegistryException(Reason.INVALID_RESPONSE,
                    "getStation returned " + (resultHex == null ? "null" : resultHex.length() / 2 - 1 + " bytes")
                            + ", expected 128; is SupplyChain deployed at the configured address?",
                    null);
        }
        byte[] result = Numeric.hexStringToByteArray(resultHex);
        BigInteger active = word(result, 0);
        BigInteger expiresAt = word(result, 2);
        BigInteger stageMask = word(result, 3);
        if (active.compareTo(BigInteger.ONE) > 0 || expiresAt.bitLength() > 64 || stageMask.bitLength() > 32) {
            throw new StationRegistryException(Reason.INVALID_RESPONSE,
                    "getStation result is not (bool, bytes32, uint64, uint32)", null);
        }
        return new StationRegistration(
                active.signum() == 1,
                Numeric.toHexString(Arrays.copyOfRange(result, 32, 64)),
                expiresAt.min(MAX_LONG).longValue(),
                stageMask.longValue());
    }

    private static BigInteger word(byte[] data, int index) {
        return new BigInteger(1, Arrays.copyOfRange(data, index * 32, (index + 1) * 32));
    }
}
