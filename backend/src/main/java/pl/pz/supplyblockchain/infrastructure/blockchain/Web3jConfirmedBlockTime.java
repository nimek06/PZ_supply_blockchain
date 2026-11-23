package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import pl.pz.supplyblockchain.application.ports.ChainUnavailableException;
import pl.pz.supplyblockchain.application.ports.ConfirmedBlockTimeSource;

/**
 * Reads the timestamp of the local confirmed head. The local Anvil scope uses {@code latest} with
 * one confirmation; {@code safe} and {@code finalized} are not used (DEC-17 / W3-03).
 */
@Component
public class Web3jConfirmedBlockTime implements ConfirmedBlockTimeSource {

    private final Web3j web3j;

    public Web3jConfirmedBlockTime(Web3j web3j) {
        this.web3j = web3j;
    }

    @Override
    public long confirmedTimestamp() {
        try {
            EthBlock response = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
            if (response == null) {
                throw unavailable("chain head returned no response", null);
            }
            if (response.hasError()) {
                throw unavailable("chain head RPC error " + response.getError().getCode()
                        + ": " + response.getError().getMessage(), null);
            }
            EthBlock.Block block = response.getBlock();
            if (block == null || block.getTimestamp() == null) {
                throw unavailable("chain head has no timestamp", null);
            }
            BigInteger timestamp = block.getTimestamp();
            if (timestamp.signum() < 0) {
                throw unavailable("chain head timestamp is negative", null);
            }
            return timestamp.longValueExact();
        } catch (ChainUnavailableException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw unavailable("chain head could not be read", exception);
        }
    }

    private static ChainUnavailableException unavailable(String message, Throwable cause) {
        return new ChainUnavailableException(message, cause);
    }
}
