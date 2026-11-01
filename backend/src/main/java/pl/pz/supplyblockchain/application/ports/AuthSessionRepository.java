package pl.pz.supplyblockchain.application.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.AuthSession;

public interface AuthSessionRepository {

    AuthSession save(AuthSession session);

    Optional<AuthSession> findByTokenHash(String tokenHash);

    void touch(UUID id, Instant now);

    boolean revoke(UUID id, Instant now);
}
