package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.StageCommand;

/**
 * Stored CREATE_BATCH idempotency entry. The request hash lets the API distinguish a replay from
 * a conflicting reuse of the same key.
 */
public record BatchIdempotency(StageCommand command, String requestHash) {
}
