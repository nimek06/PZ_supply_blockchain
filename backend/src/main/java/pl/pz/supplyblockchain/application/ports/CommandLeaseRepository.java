package pl.pz.supplyblockchain.application.ports;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.StageCommand;

/**
 * Claim and release of commands for the single local worker. Acquisition and the lease update are
 * one short database transaction; all I/O happens after it returns.
 */
public interface CommandLeaseRepository {

    /**
     * Claims the oldest command whose status is {@code ACCEPTED}, {@code PINNING} or
     * {@code READY_TO_SUBMIT}, whose lease is free or expired and whose {@code retry_at} is due.
     */
    Optional<StageCommand> acquireNext(String leaseOwner, Instant now, Duration leaseDuration);

    /** Releases a lease held by {@code leaseOwner}. An expired lease is already free. */
    boolean release(UUID commandId, String leaseOwner, Instant now);
}
