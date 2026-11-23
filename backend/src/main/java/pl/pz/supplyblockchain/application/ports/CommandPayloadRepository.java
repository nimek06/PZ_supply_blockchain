package pl.pz.supplyblockchain.application.ports;

import java.util.Optional;
import java.util.UUID;

/** Reads the stored canonical payload of a command for the worker. */
public interface CommandPayloadRepository {

    Optional<WorkerCommandPayload> findById(UUID commandId);
}
