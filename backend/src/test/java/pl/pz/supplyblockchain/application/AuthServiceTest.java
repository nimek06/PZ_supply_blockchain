package pl.pz.supplyblockchain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.application.auth.NewAuthSession;
import pl.pz.supplyblockchain.application.ports.AuthChallengeRepository;
import pl.pz.supplyblockchain.application.ports.AuthSessionRepository;
import pl.pz.supplyblockchain.application.ports.SiweMessagePort;
import pl.pz.supplyblockchain.application.ports.SiweVerification;
import pl.pz.supplyblockchain.config.AuthProperties;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.AuthChallenge;
import pl.pz.supplyblockchain.domain.AuthSession;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.infrastructure.auth.Sha256TokenHasher;

class AuthServiceTest {

    private final AuthChallengeRepository challengeRepository = mock(AuthChallengeRepository.class);
    private final AuthSessionRepository sessionRepository = mock(AuthSessionRepository.class);
    private final SiweMessagePort siweMessagePort = mock(SiweMessagePort.class);
    private final Sha256TokenHasher tokenHasher = new Sha256TokenHasher();
    private final AuthProperties authProperties = new AuthProperties(
            "localhost", "http://localhost:5173", "Sign in", 300, 28800, 1800);
    private final ChainProperties chainProperties = new ChainProperties(
            31337, "0x5fbdb2315678afecb367f032d93f642f64180aa3", "SupplyChain", "1", 3600);
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1800000000L), ZoneOffset.UTC);

    private AuthService authService;

    private final String address = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                challengeRepository, sessionRepository, siweMessagePort, tokenHasher,
                authProperties, chainProperties, clock);
    }

    @Test
    void createChallengeSuccess() {
        when(siweMessagePort.createMessage(any())).thenReturn("localhost wants you to sign in...");
        when(challengeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuthChallenge challenge = authService.createChallenge(address);

        assertThat(challenge.address()).isEqualTo(address);
        assertThat(challenge.message()).startsWith("localhost");
        assertThat(challenge.nonce()).hasSize(32);
    }

    @Test
    void createSessionSuccess() {
        UUID challengeId = UUID.randomUUID();
        String message = "localhost wants you to sign in...";
        AuthChallenge challenge = new AuthChallenge(
                challengeId, address, "nonce123", message, "localhost", "http://localhost:5173",
                31337, Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000300L), null);

        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(siweMessagePort.verifyMessage(any(), any(), any(Long.class))).thenReturn(
                new SiweVerification(address, "localhost", "nonce123", 31337, true, true));
        when(challengeRepository.consume(any(), any())).thenReturn(true);
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NewAuthSession session = authService.createSession(challengeId, message, "0xsig");

        assertThat(session.token()).isNotBlank();
        assertThat(session.session().address()).isEqualTo(address);
    }

    @Test
    void createSessionRejectsExpiredChallenge() {
        UUID challengeId = UUID.randomUUID();
        AuthChallenge challenge = new AuthChallenge(
                challengeId, address, "nonce123", "msg", "localhost", "http://localhost:5173",
                31337, Instant.ofEpochSecond(1799990000L), Instant.ofEpochSecond(1799999999L), null);

        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> authService.createSession(challengeId, "msg", "0xsig"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.CHALLENGE_USED_OR_EXPIRED));
    }

    @Test
    void authenticateOptionalValidToken() {
        String token = "test-bearer-token";
        String tokenHash = tokenHasher.sha256Hex(token);
        AuthSession session = new AuthSession(
                UUID.randomUUID(), tokenHash, address, 31337,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800028800L), 1800,
                Instant.ofEpochSecond(1800000000L), null);

        when(sessionRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(session));

        Optional<String> authenticated = authService.authenticateOptional("Bearer " + token);

        assertThat(authenticated).contains(address);
    }
}
