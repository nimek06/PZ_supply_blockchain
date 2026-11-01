package pl.pz.supplyblockchain.application.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.AuthChallenge;

public interface AuthChallengeRepository {

    AuthChallenge save(AuthChallenge challenge);

    Optional<AuthChallenge> findById(UUID id);

    boolean consume(UUID id, Instant now);
}
