package pl.pz.supplyblockchain.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Relayer fee policy. Polygon/Amoy requires a high priority fee, so the relayer never goes below
 * the configured floor even when the RPC suggests less.
 */
@Validated
@ConfigurationProperties(prefix = "supplychain.relayer")
public record RelayerProperties(@Positive long minTipGwei) {
}
