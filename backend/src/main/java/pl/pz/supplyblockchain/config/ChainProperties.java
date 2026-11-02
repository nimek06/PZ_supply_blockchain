package pl.pz.supplyblockchain.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import pl.pz.supplyblockchain.domain.DeploymentId;

@Validated
@ConfigurationProperties(prefix = "supplychain.chain")
public record ChainProperties(
        @Positive long chainId,
        @NotBlank String contractAddress,
        @NotBlank String domainName,
        @NotBlank String domainVersion,
        @Positive long maxDeadlineWindowSeconds) {

    public ChainProperties {
        contractAddress = contractAddress == null ? null : contractAddress.toLowerCase(Locale.ROOT);
    }

    public DeploymentId deploymentId() {
        return new DeploymentId(chainId, contractAddress);
    }
}
