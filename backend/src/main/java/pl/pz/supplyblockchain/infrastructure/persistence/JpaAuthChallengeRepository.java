package pl.pz.supplyblockchain.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.AuthChallengeRepository;
import pl.pz.supplyblockchain.domain.AuthChallenge;

@Repository
public class JpaAuthChallengeRepository implements AuthChallengeRepository {

    private final AuthChallengeJpaRepository repository;

    public JpaAuthChallengeRepository(AuthChallengeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public AuthChallenge save(AuthChallenge challenge) {
        return repository.save(AuthChallengeEntity.from(challenge)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthChallenge> findById(UUID id) {
        return repository.findById(id).map(AuthChallengeEntity::toDomain);
    }

    @Override
    @Transactional
    public boolean consume(UUID id, Instant now) {
        return repository.consume(id, now) == 1;
    }
}
