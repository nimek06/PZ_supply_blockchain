package pl.pz.supplyblockchain.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record DeploymentId(long chainId, String contractAddress) {

    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-f]{40}$");

    public DeploymentId {
        if (chainId <= 0) {
            throw new IllegalArgumentException("chainId must be positive");
        }
        if (contractAddress == null) {
            throw new IllegalArgumentException("contractAddress is required");
        }
        contractAddress = contractAddress.toLowerCase(Locale.ROOT);
        if (!ADDRESS.matcher(contractAddress).matches()) {
            throw new IllegalArgumentException("contractAddress must be a 20-byte hex address");
        }
    }
}
