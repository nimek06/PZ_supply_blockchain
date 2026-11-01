package pl.pz.supplyblockchain.application.ports;

public record SiweVerification(
        String address,
        String domain,
        String nonce,
        long chainId,
        boolean issuedAtValid,
        boolean expirationValid) {
}
