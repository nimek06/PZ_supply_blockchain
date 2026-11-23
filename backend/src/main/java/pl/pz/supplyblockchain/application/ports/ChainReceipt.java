package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.TransactionReceiptStatus;

/** Receipt data needed to reconcile a submitted attempt. */
public record ChainReceipt(
        TransactionReceiptStatus status,
        long blockNumber,
        String blockHash) {
}
