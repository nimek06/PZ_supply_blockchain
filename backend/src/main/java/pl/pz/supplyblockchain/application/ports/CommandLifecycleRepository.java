package pl.pz.supplyblockchain.application.ports;

import java.time.Instant;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.StageCommand;

/**
 * Lease-guarded status updates for the worker. {@code CONFIRMED} is deliberately not allowed here:
 * only the indexer may set it from a canonical event.
 */
public interface CommandLifecycleRepository {

    StageCommand transition(
            UUID commandId,
            String leaseOwner,
            CommandStatus expectedStatus,
            CommandStatus nextStatus,
            String cid,
            String failureCode,
            Long retryAllowedAfter,
            Instant retryAt,
            Instant now);
}
