package pl.pz.supplyblockchain.application.ports;

public interface TokenHasher {

    String sha256Hex(String token);
}
