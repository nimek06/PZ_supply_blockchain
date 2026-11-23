package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.StageCommand;

/**
 * Stored command plus the exact canonical bytes and station signature needed by the worker.
 * The byte array is never re-serialized before pinning.
 */
public record WorkerCommandPayload(
        StageCommand command,
        byte[] reportJcs,
        String stationSignature) {
}
