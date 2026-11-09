package pl.pz.supplyblockchain.application.ports;

/**
 * Failure of {@link StationRegistry}. It is never a verdict about the station: callers map it to
 * {@code DEPENDENCY_UNAVAILABLE} and must not treat the station as active or revoked.
 */
public class StationRegistryException extends RuntimeException {

    public enum Reason {
        /** RPC unreachable, timeout or JSON-RPC error. */
        UNAVAILABLE(true),
        /** Empty or malformed {@code eth_call} result, e.g. no contract at the configured address. */
        INVALID_RESPONSE(false);

        private final boolean retryable;

        Reason(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    private final Reason reason;

    public StationRegistryException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
