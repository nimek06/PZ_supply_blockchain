package pl.pz.supplyblockchain.application.ports;

public record CanonicalReport(byte[] jcsBytes, String reportHash) {
}
