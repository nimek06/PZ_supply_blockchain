package pl.pz.supplyblockchain.application.ports;

/**
 * Time of the locally confirmed chain head. The local Anvil scope uses {@code latest} with one
 * confirmation (DEC-17), so a block that is the head is already confirmed for this policy.
 */
public interface ConfirmedBlockTimeSource {

    /** Unix seconds of the confirmed chain head. */
    long confirmedTimestamp();
}
