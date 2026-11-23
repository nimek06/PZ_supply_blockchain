package pl.pz.supplyblockchain.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.CommandPayloadRepository;
import pl.pz.supplyblockchain.application.ports.WorkerCommandPayload;

@Repository
public class JpaCommandPayloadRepository implements CommandPayloadRepository {

    private final StageCommandJpaRepository repository;

    public JpaCommandPayloadRepository(StageCommandJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkerCommandPayload> findById(UUID commandId) {
        return repository.findById(commandId)
                .map(entity -> new WorkerCommandPayload(
                        entity.toDomain(), entity.reportJcs(), entity.stationSignature()));
    }
}
