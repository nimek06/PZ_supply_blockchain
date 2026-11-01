package pl.pz.supplyblockchain.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.AuthSessionRepository;
import pl.pz.supplyblockchain.domain.AuthSession;

@Repository
public class JpaAuthSessionRepository implements AuthSessionRepository {

    private final AuthSessionJpaRepository repository;

    public JpaAuthSessionRepository(AuthSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public AuthSession save(AuthSession session) {
        return repository.save(AuthSessionEntity.from(session)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthSession> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(AuthSessionEntity::toDomain);
    }

    @Override
    @Transactional
    public void touch(UUID id, Instant now) {
        repository.touch(id, now);
    }

    @Override
    @Transactional
    public boolean revoke(UUID id, Instant now) {
        return repository.revoke(id, now) == 1;
    }
}
