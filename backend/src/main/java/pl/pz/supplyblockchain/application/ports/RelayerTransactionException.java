package pl.pz.supplyblockchain.application.ports;

/**
 * A durable failure classification for the relayer. A retryable failure keeps the current command
 * stage and is retried later; a permanent failure is recorded as FAILED with a stable code.
 */
public class RelayerTransactionException extends RuntimeException {

    private final boolean retryable;
    private final String failureCode;

    public RelayerTransactionException(boolean retryable, String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.failureCode = failureCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public String failureCode() {
        return failureCode;
    }
}
