package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.math.BigInteger;
import java.util.Locale;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;

public final class EcdsaRecovery {

    private static final BigInteger HALF_N =
            new BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16);

    private EcdsaRecovery() {
    }

    /**
     * Recovers an address from a 65-byte r||s||v signature over an already-computed digest.
     * High-s signatures are rejected so that the accepted signature set is canonical.
     */
    public static String recoverAddress(byte[] digest, String signatureHex) {
        if (digest == null || digest.length != 32) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "signature digest must be 32 bytes");
        }
        byte[] signature;
        try {
            signature = Numeric.hexStringToByteArray(signatureHex);
        } catch (RuntimeException exception) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "signature is not valid hex");
        }
        if (signature.length != 65) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "signature must be 65 bytes");
        }

        int v = signature[64] & 0xff;
        if (v >= 27) {
            v -= 27;
        }
        if (v != 0 && v != 1) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "signature recovery id is invalid");
        }

        BigInteger r = new BigInteger(1, java.util.Arrays.copyOfRange(signature, 0, 32));
        BigInteger s = new BigInteger(1, java.util.Arrays.copyOfRange(signature, 32, 64));
        if (r.signum() == 0 || s.signum() == 0 || s.compareTo(HALF_N) > 0) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "signature values are not canonical");
        }

        try {
            BigInteger publicKey = Sign.recoverFromSignature(v, new ECDSASignature(r, s), digest);
            if (publicKey == null) {
                throw new DomainException(ErrorCode.INVALID_SIGNATURE, "signature could not be recovered");
            }
            return ("0x" + Keys.getAddress(publicKey)).toLowerCase(Locale.ROOT);
        } catch (DomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "signature could not be recovered");
        }
    }
}
