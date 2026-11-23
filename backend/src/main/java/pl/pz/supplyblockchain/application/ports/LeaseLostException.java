package pl.pz.supplyblockchain.application.ports;

/**
 * A worker tried to change a command without holding a current lease for it. The worker must not
 * retry the same transition until it acquires the command again.
 */
public class LeaseLostException extends RuntimeException {

    public LeaseLostException(String message) {
        super(message);
    }
}
