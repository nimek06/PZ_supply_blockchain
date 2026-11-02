package pl.pz.supplyblockchain.application.ports;

/**
 * Failure of {@link ReportStorage}. {@link Reason#retryable()} tells the worker whether it may schedule a retry
 * of the same command; it never means a new stage command.
 */
public class ReportStorageException extends RuntimeException {

    public enum Reason {
        /** Connect/read timeout: the service may have pinned the bytes anyway; retry with the same bytes. */
        TIMEOUT(true),
        /** Service unreachable or HTTP 5xx. */
        UNAVAILABLE(true),
        /** HTTP 429 or plan quota reached (Pinata free plan). Retry only after a back-off. */
        RATE_LIMITED(true),
        /** HTTP 401/403: missing or wrong credentials; configuration error, no retry. */
        UNAUTHORIZED(false),
        /** Report bytes rejected, e.g. above the configured size limit. */
        REJECTED(false),
        /** Response without a CID, with a malformed CID or unreadable body. */
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

    public ReportStorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
