package pl.pz.supplyblockchain.application.ports;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Recovery queue for SUBMITTED commands. It is intentionally separate from
 * {@link CommandLeaseRepository#acquireNext}: normal acquisition never takes SUBMITTED work.
 */
public interface SubmittedRecoveryRepository {

    Optional<SubmittedAttempt> claimNext(String leaseOwner, Instant now, Duration leaseDuration);
}
