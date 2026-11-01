package pl.pz.supplyblockchain.domain;

public enum CommandStatus {
    ACCEPTED,
    PINNING,
    WAITING_FOR_QA,
    READY_TO_SUBMIT,
    SUBMITTED,
    CONFIRMED,
    FAILED
}
