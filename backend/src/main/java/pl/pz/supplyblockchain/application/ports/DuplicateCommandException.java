package pl.pz.supplyblockchain.application.ports;

public class DuplicateCommandException extends RuntimeException {

    public DuplicateCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
