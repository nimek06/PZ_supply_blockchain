package pl.pz.supplyblockchain.application.ports;

import java.time.Instant;

public record IndexerCheckpoint(long blockNumber, String blockHash, Instant updatedAt) {
}
