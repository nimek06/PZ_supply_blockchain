package pl.pz.supplyblockchain.application.ports;

import java.time.Instant;
import pl.pz.supplyblockchain.domain.TransactionAttempt;

/**
 * Writes the signed attempt and the READY_TO_SUBMIT -> SUBMITTED transition in one short transaction.
 * The caller broadcasts only after this method returns.
 */
public interface CommandSubmissionRepository {

    TransactionAttempt reserveAndMarkSubmitted(
            TransactionAttempt attempt,
            String leaseOwner,
            Instant now);
}
