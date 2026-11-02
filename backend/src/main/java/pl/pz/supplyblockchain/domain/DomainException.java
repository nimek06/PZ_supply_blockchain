package pl.pz.supplyblockchain.domain;

public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final Long retryAllowedAfter;

    public DomainException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public DomainException(ErrorCode code, String message, Long retryAllowedAfter) {
        super(message);
        this.code = code;
        this.retryAllowedAfter = retryAllowedAfter;
    }

    public ErrorCode code() {
        return code;
    }

    public Long retryAllowedAfter() {
        return retryAllowedAfter;
    }
}
