package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.StageCommand;
import pl.pz.supplyblockchain.domain.TransactionAttempt;

/** A SUBMITTED command and the attempt that must be reconciled. */
public record SubmittedAttempt(StageCommand command, TransactionAttempt attempt) {
}
