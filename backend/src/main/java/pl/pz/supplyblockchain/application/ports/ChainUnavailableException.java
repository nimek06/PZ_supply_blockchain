package pl.pz.supplyblockchain.application.ports;

/**
 * The chain head could not be read. This is never evidence that a condition is satisfied; callers
 * map it to {@code DEPENDENCY_UNAVAILABLE} and must not treat the failure as a positive result.
 */
public class ChainUnavailableException extends RuntimeException {

    public ChainUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
