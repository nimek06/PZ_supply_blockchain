package pl.pz.supplyblockchain.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record BatchId(String value) {

    private static final Pattern BYTES32 = Pattern.compile("^0x[0-9a-f]{64}$");

    public BatchId {
        if (value == null) {
            throw new IllegalArgumentException("batchId is required");
        }
        value = value.toLowerCase(Locale.ROOT);
        if (!BYTES32.matcher(value).matches()) {
            throw new IllegalArgumentException("batchId must be a lowercase bytes32 hex value");
        }
    }
}
