package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import pl.pz.supplyblockchain.domain.DeploymentId;

/**
 * Trust settings for station certificates of one deployment: certificates must name this chainId and
 * contract, and be signed by one of {@code trustedIssuers}. An empty allowlist is a configuration error,
 * not "trust everyone".
 */
public record StationCertificatePolicy(DeploymentId deployment, Set<String> trustedIssuers) {

    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-f]{40}$");
    private static final String ZERO_ADDRESS = "0x" + "0".repeat(40);

    public StationCertificatePolicy {
        Objects.requireNonNull(deployment, "deployment");
        if (trustedIssuers == null || trustedIssuers.isEmpty()) {
            throw new IllegalArgumentException("at least one trusted certificate issuer is required");
        }
        trustedIssuers = trustedIssuers.stream()
                .map(StationCertificatePolicy::normalizeIssuer)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean trusts(String issuerAddress) {
        return issuerAddress != null && trustedIssuers.contains(issuerAddress.toLowerCase(Locale.ROOT));
    }

    private static String normalizeIssuer(String address) {
        String normalized = address == null ? "" : address.toLowerCase(Locale.ROOT);
        if (!ADDRESS.matcher(normalized).matches() || ZERO_ADDRESS.equals(normalized)) {
            throw new IllegalArgumentException("trusted issuer must be a non-zero 20-byte hex address");
        }
        return normalized;
    }
}
