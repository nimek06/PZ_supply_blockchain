package pl.pz.supplyblockchain.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "supplychain.indexer")
public record ChainIndexerProperties(
        boolean enabled,
        @Min(1) long deploymentBlock,
        @Positive long pollIntervalMs,
        @Positive @Max(10_000) int blockRange,
        @Positive @Max(64) int reorgWindowBlocks,
        @Positive long staleAfterSeconds) {

    public ChainIndexerProperties {
        if (reorgWindowBlocks < 2) {
            throw new IllegalArgumentException("reorgWindowBlocks must be at least 2");
        }
    }
}
