package pl.pz.supplyblockchain.infrastructure.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import pl.pz.supplyblockchain.application.ports.TokenHasher;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;

@Component
public class Sha256TokenHasher implements TokenHasher {

    @Override
    public String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "SHA-256 is unavailable");
        }
    }
}
