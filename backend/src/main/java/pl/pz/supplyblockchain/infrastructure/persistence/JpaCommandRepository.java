package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.application.ports.DuplicateCommandException;
import pl.pz.supplyblockchain.application.ports.NewStageCommand;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StageCommand;

@Repository
public class JpaCommandRepository implements CommandRepository {

    private final StageCommandJpaRepository repository;
    private final EntityManager entityManager;

    public JpaCommandRepository(StageCommandJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StageCommand> findById(UUID id) {
        return repository.findById(id).map(StageCommandEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StageCommand> findBySignedPayloadDigest(DeploymentId deploymentId, String digest) {
        return repository.findByChainIdAndContractAddressAndSignedPayloadDigest(
                        deploymentId.chainId(), deploymentId.contractAddress(), digest)
                .map(StageCommandEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StageCommand> findLatestStageCommand(
            DeploymentId deploymentId, String batchId, int stageId) {
        return repository.findFirstByChainIdAndContractAddressAndBatchIdAndStageIdAndTypeOrderByCreatedAtDesc(
                        deploymentId.chainId(), deploymentId.contractAddress(), batchId, stageId, CommandType.STAGE)
                .map(StageCommandEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StageCommand saveNew(NewStageCommand command) {
        StageCommandEntity entity = StageCommandEntity.from(command);
        entityManager.persist(entity);
        try {
            entityManager.flush();
        } catch (DataIntegrityViolationException | PersistenceException exception) {
            throw new DuplicateCommandException("command conflicts with an existing row", exception);
        }
        return entity.toDomain();
    }
}
