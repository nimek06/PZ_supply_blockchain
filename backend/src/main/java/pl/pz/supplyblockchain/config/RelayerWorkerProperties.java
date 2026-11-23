package pl.pz.supplyblockchain.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "supplychain.relayer.worker")
public record RelayerWorkerProperties(
        boolean enabled,
        @Positive long pollIntervalMs,
        @Positive long leaseSeconds,
        @Positive long retryBackoffSeconds,
        @Min(100) int gasMarginPercent,
        String leaseOwner) {

    public String effectiveLeaseOwner() {
        if (leaseOwner != null && !leaseOwner.isBlank()) {
            return leaseOwner;
        }
        return "relayer-worker-" + ProcessHandle.current().pid();
    }
}
