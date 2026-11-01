package pl.pz.supplyblockchain.infrastructure.auth;

import com.moonstoneid.siwe.SiweMessage;
import com.moonstoneid.siwe.error.SiweException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import pl.pz.supplyblockchain.application.ports.SiweChallengeSpec;
import pl.pz.supplyblockchain.application.ports.SiweMessagePort;
import pl.pz.supplyblockchain.application.ports.SiweVerification;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.infrastructure.blockchain.EcdsaRecovery;

@Component
public class SiweJavaMessageAdapter implements SiweMessagePort {

    private final Clock clock;

    public SiweJavaMessageAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String createMessage(SiweChallengeSpec spec) {
        try {
            String checksumAddress = Keys.toChecksumAddress(spec.address());
            if (!checksumAddress.startsWith("0x")) {
                checksumAddress = "0x" + checksumAddress;
            }
            return new SiweMessage.Builder(
                    spec.domain(),
                    checksumAddress,
                    spec.uri(),
                    "1",
                    Math.toIntExact(spec.chainId()),
                    spec.nonce(),
                    iso(spec.issuedAt()))
                    .statement(spec.statement())
                    .expirationTime(iso(spec.expiresAt()))
                    .build()
                    .toMessage();
        } catch (SiweException | ArithmeticException exception) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "could not create a SIWE challenge");
        }
    }

    @Override
    public SiweVerification verifyMessage(String message, String signature, long expectedChainId) {
        try {
            SiweMessage parsed = new SiweMessage.Parser().parse(message);
            byte[] digest = Sign.getEthereumMessageHash(message.getBytes(StandardCharsets.UTF_8));
            String signer = EcdsaRecovery.recoverAddress(digest, signature);
            if (!parsed.getAddress().equalsIgnoreCase(signer)) {
                throw new DomainException(ErrorCode.INVALID_SIGNATURE, "SIWE signature does not match the message address");
            }

            Instant now = clock.instant();
            Instant issuedAt = OffsetDateTime.parse(parsed.getIssuedAt()).toInstant();
            boolean issuedAtValid = !issuedAt.isAfter(now.plusSeconds(60))
                    && !issuedAt.isBefore(now.minusSeconds(900));
            boolean expirationValid = parsed.getExpirationTime() != null
                    && OffsetDateTime.parse(parsed.getExpirationTime()).toInstant().isAfter(now);

            return new SiweVerification(
                    signer,
                    parsed.getDomain(),
                    parsed.getNonce(),
                    parsed.getChainId(),
                    issuedAtValid,
                    expirationValid);
        } catch (SiweException exception) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "SIWE message is malformed");
        } catch (DateTimeParseException exception) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "SIWE timestamp is malformed");
        }
    }

    private static String iso(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC).toString();
    }
}
