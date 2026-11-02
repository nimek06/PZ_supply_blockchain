package pl.pz.supplyblockchain.application.ports;

public interface SiweMessagePort {

    String createMessage(SiweChallengeSpec spec);

    SiweVerification verifyMessage(String message, String signature, long expectedChainId);
}
