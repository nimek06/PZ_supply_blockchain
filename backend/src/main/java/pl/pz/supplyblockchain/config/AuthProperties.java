package pl.pz.supplyblockchain.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "supplychain.auth")
public record AuthProperties(
        @NotBlank String domain,
        @NotBlank String uri,
        @NotBlank String statement,
        @Positive long challengeTtlSeconds,
        @Positive long tokenTtlSeconds,
        @Positive long idleTimeoutSeconds) {
}
