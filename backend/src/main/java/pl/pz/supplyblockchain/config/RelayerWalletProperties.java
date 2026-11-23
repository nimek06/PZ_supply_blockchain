package pl.pz.supplyblockchain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Relayer signing key. It is only read when the worker is enabled and must never be logged or stored.
 */
@ConfigurationProperties(prefix = "supplychain.relayer")
public record RelayerWalletProperties(String privateKey) {
}
