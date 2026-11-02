package pl.pz.supplyblockchain.application.ports;

public record SignedTransaction(String rawTransaction, String transactionHash) {
}
