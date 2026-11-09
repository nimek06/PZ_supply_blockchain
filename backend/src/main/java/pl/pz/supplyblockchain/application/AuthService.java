package pl.pz.supplyblockchain.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.auth.NewAuthSession;
import pl.pz.supplyblockchain.application.ports.AuthChallengeRepository;
import pl.pz.supplyblockchain.application.ports.AuthSessionRepository;
import pl.pz.supplyblockchain.application.ports.SiweChallengeSpec;
import pl.pz.supplyblockchain.application.ports.SiweMessagePort;
import pl.pz.supplyblockchain.application.ports.SiweVerification;
import pl.pz.supplyblockchain.application.ports.TokenHasher;
import pl.pz.supplyblockchain.config.AuthProperties;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.AuthChallenge;
import pl.pz.supplyblockchain.domain.AuthSession;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;

@Service
public class AuthService {

    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthChallengeRepository challengeRepository;
    private final AuthSessionRepository sessionRepository;
    private final SiweMessagePort siweMessagePort;
    private final TokenHasher tokenHasher;
    private final AuthProperties authProperties;
    private final ChainProperties chainProperties;
    private final Clock clock;

    public AuthService(
            AuthChallengeRepository challengeRepository,
            AuthSessionRepository sessionRepository,
            SiweMessagePort siweMessagePort,
            TokenHasher tokenHasher,
            AuthProperties authProperties,
            ChainProperties chainProperties,
            Clock clock) {
        this.challengeRepository = challengeRepository;
        this.sessionRepository = sessionRepository;
        this.siweMessagePort = siweMessagePort;
        this.tokenHasher = tokenHasher;
        this.authProperties = authProperties;
        this.chainProperties = chainProperties;
        this.clock = clock;
    }

    @Transactional
    public AuthChallenge createChallenge(String address) {
        String normalizedAddress = normalizeAddress(address);
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(authProperties.challengeTtlSeconds());
        String nonce = randomHex(16);
        SiweChallengeSpec spec = new SiweChallengeSpec(
                authProperties.domain(),
                normalizedAddress,
                authProperties.uri(),
                chainProperties.chainId(),
                nonce,
                authProperties.statement(),
                issuedAt,
                expiresAt);
        String message = siweMessagePort.createMessage(spec);
        AuthChallenge challenge = new AuthChallenge(
                UUID.randomUUID(),
                normalizedAddress,
                nonce,
                message,
                authProperties.domain(),
                authProperties.uri(),
                chainProperties.chainId(),
                issuedAt,
                expiresAt,
                null);
        return challengeRepository.save(challenge);
    }

    @Transactional
    public NewAuthSession createSession(UUID challengeId, String message, String signature) {
        AuthChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new DomainException(ErrorCode.CHALLENGE_USED_OR_EXPIRED,
                        "challenge is unknown, used or expired"));
        Instant now = clock.instant();
        if (challenge.usedAt() != null || !now.isBefore(challenge.expiresAt())) {
            throw new DomainException(ErrorCode.CHALLENGE_USED_OR_EXPIRED,
                    "challenge is unknown, used or expired");
        }
        if (message == null || !MessageDigest.isEqual(
                challenge.message().getBytes(StandardCharsets.UTF_8),
                message.getBytes(StandardCharsets.UTF_8))) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "message does not match the issued challenge");
        }

        SiweVerification verification = siweMessagePort.verifyMessage(
                message, signature, chainProperties.chainId());
        if (!verification.address().equalsIgnoreCase(challenge.address())
                || !verification.domain().equals(challenge.domain())
                || !verification.nonce().equals(challenge.nonce())
                || verification.chainId() != challenge.chainId()
                || !verification.issuedAtValid()
                || !verification.expirationValid()) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "SIWE message verification failed");
        }
        if (!challengeRepository.consume(challenge.id(), now)) {
            throw new DomainException(ErrorCode.CHALLENGE_USED_OR_EXPIRED,
                    "challenge is unknown, used or expired");
        }

        String token = randomToken();
        String tokenHash = tokenHasher.sha256Hex(token);
        Instant sessionNow = now.truncatedTo(ChronoUnit.SECONDS);
        AuthSession session = new AuthSession(
                UUID.randomUUID(),
                tokenHash,
                challenge.address().toLowerCase(Locale.ROOT),
                challenge.chainId(),
                sessionNow,
                sessionNow.plusSeconds(authProperties.tokenTtlSeconds()),
                authProperties.idleTimeoutSeconds(),
                sessionNow,
                null);
        return new NewAuthSession(sessionRepository.save(session), token);
    }

    @Transactional
    public Optional<AuthSession> authenticateSessionOptional(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        String token = extractBearerToken(authorizationHeader);
        Instant now = clock.instant();
        AuthSession session = sessionRepository.findByTokenHash(tokenHasher.sha256Hex(token))
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED, "invalid bearer token"));
        if (session.revokedAt() != null
                || !now.isBefore(session.expiresAt())
                || now.isAfter(session.lastSeenAt().plusSeconds(session.idleTimeoutSeconds()))) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "invalid or expired bearer token");
        }
        sessionRepository.touch(session.id(), now);
        return Optional.of(session);
    }

    public Optional<String> authenticateOptional(String authorizationHeader) {
        return authenticateSessionOptional(authorizationHeader).map(AuthSession::address);
    }

    public AuthSession authenticateSession(String authorizationHeader) {
        return authenticateSessionOptional(authorizationHeader)
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED, "bearer token is required"));
    }

    public String authenticateRequired(String authorizationHeader) {
        return authenticateSession(authorizationHeader).address();
    }

    @Transactional
    public void revoke(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        Instant now = clock.instant();
        AuthSession session = sessionRepository.findByTokenHash(tokenHasher.sha256Hex(token))
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED, "invalid bearer token"));
        if (!sessionRepository.revoke(session.id(), now)) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "invalid bearer token");
        }
    }

    private static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "authorization header must use Bearer auth");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty() || token.length() > 512) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "bearer token is invalid");
        }
        return token;
    }

    private static String normalizeAddress(String address) {
        if (address == null || !ADDRESS.matcher(address).matches()) {
            throw new DomainException(ErrorCode.INVALID_REQUEST, "address must be a 20-byte hex value");
        }
        return address.toLowerCase(Locale.ROOT);
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static String randomToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
