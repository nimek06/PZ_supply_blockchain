package pl.pz.supplyblockchain.infrastructure.ipfs;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Accepts the CIDs our storage can return for a report (DEC-14): CIDv0 ({@code Qm...}, base58btc, dag-pb)
 * and CIDv1 in base32 ({@code b...}) with the raw or dag-pb codec. Both must carry a sha2-256 multihash.
 * Anything else, including gateway URLs and {@code ipfs://} links, is rejected.
 */
public final class CidValidator {

    private static final String BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String BASE32 = "abcdefghijklmnopqrstuvwxyz234567";
    private static final int MAX_LENGTH = 100;

    // All of these values are below 0x80, so each is encoded as a single-byte varint.
    private static final int CID_V1 = 0x01;
    private static final int RAW = 0x55;
    private static final int DAG_PB = 0x70;
    private static final int SHA2_256 = 0x12;
    private static final int DIGEST_LENGTH = 32;

    private CidValidator() {
    }

    public static boolean isValid(String cid) {
        if (cid == null || cid.length() > MAX_LENGTH) {
            return false;
        }
        if (cid.length() == 46 && cid.startsWith("Qm")) {
            return isValidV0(cid);
        }
        if (cid.length() > 1 && cid.charAt(0) == 'b') {
            return isValidV1(cid.substring(1));
        }
        return false;
    }

    private static boolean isValidV0(String cid) {
        byte[] multihash = decodeBase58(cid);
        return multihash != null
                && multihash.length == 2 + DIGEST_LENGTH
                && (multihash[0] & 0xff) == SHA2_256
                && (multihash[1] & 0xff) == DIGEST_LENGTH;
    }

    private static boolean isValidV1(String base32) {
        byte[] bytes = decodeBase32(base32);
        if (bytes == null || bytes.length != 4 + DIGEST_LENGTH) {
            return false;
        }
        int codec = bytes[1] & 0xff;
        return (bytes[0] & 0xff) == CID_V1
                && (codec == RAW || codec == DAG_PB)
                && (bytes[2] & 0xff) == SHA2_256
                && (bytes[3] & 0xff) == DIGEST_LENGTH;
    }

    private static byte[] decodeBase58(String value) {
        BigInteger number = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(58);
        for (int i = 0; i < value.length(); i++) {
            int digit = BASE58.indexOf(value.charAt(i));
            if (digit < 0) {
                return null;
            }
            number = number.multiply(base).add(BigInteger.valueOf(digit));
        }
        byte[] bytes = number.toByteArray();
        // toByteArray adds a sign byte when the top bit is set
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    private static byte[] decodeBase32(String value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = BASE32.indexOf(value.charAt(i));
            if (digit < 0) {
                return null;
            }
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                output.write((buffer >> bits) & 0xff);
            }
        }
        // unpadded base32: leftover bits must be fewer than 5 and all zero
        if (bits >= 5 || (buffer & ((1 << bits) - 1)) != 0) {
            return null;
        }
        return output.toByteArray();
    }
}
