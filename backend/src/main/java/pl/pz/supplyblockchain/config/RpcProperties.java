package pl.pz.supplyblockchain.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JSON-RPC endpoint used for read-only on-chain checks (roles, registry). It may contain a
 * provider token, so it is never logged in full.
 */
@Validated
@ConfigurationProperties(prefix = "supplychain.rpc")
public record RpcProperties(@NotBlank String url) {
}
