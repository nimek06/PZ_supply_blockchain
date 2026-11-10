package pl.pz.supplyblockchain.domain;

import java.util.regex.Pattern;

/**
 * Human-readable batch number {@code B-RRRR-NNNN}. The backend assigns it; {@code batchId} is
 * {@code keccak256(utf8(batchNumber))}.
 */
public record BatchNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("^B-[0-9]{4}-[0-9]{4}$");

    public BatchNumber {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("batch number must match B-RRRR-NNNN");
        }
    }

    public static BatchNumber of(int year, int sequence) {
        if (year < 2000 || year > 9999) {
            throw new IllegalArgumentException("year must be between 2000 and 9999");
        }
        if (sequence < 1 || sequence > 9999) {
            throw new IllegalArgumentException("sequence must be between 1 and 9999");
        }
        return new BatchNumber(String.format("B-%04d-%04d", year, sequence));
    }
}
