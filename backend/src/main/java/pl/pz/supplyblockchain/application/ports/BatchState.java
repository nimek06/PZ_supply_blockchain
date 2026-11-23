package pl.pz.supplyblockchain.application.ports;

/** Minimal on-chain batch state needed by the worker preflight. */
public record BatchState(boolean exists, int nextStage, boolean completed) {
}
