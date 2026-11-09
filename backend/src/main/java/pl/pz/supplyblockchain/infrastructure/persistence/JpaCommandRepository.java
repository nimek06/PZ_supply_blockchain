package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.BatchIdempotency;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.application.ports.DuplicateCommandException;
import pl.pz.supplyblockchain.application.ports.NewCreateBatchCommand;
import pl.pz.supplyblockchain.application.ports.NewStageCommand;
import pl.pz.supplyblockchain.domain.CommandOutcome;
import pl.pz.supplyblockchain.domain.CommandStatus;
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
        return persist(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StageCommand saveNewCreateBatch(NewCreateBatchCommand command) {
        StageCommandEntity entity = StageCommandEntity.from(command);
        return persist(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StageCommand> findCreateBatch(DeploymentId deploymentId, String batchId) {
        return repository.findFirstByChainIdAndContractAddressAndTypeAndBatchIdOrderByCreatedAtDesc(
                        deploymentId.chainId(), deploymentId.contractAddress(), CommandType.CREATE_BATCH, batchId)
                .map(StageCommandEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StageCommand> findCreateBatchByNumber(DeploymentId deploymentId, String batchNumber) {
        return repository.findFirstByChainIdAndContractAddressAndTypeAndBatchNumberOrderByCreatedAtDesc(
                        deploymentId.chainId(), deploymentId.contractAddress(), CommandType.CREATE_BATCH, batchNumber)
                .map(StageCommandEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BatchIdempotency> findCreateBatchByIdempotency(
            DeploymentId deploymentId, String ownerAddress, String idempotencyKey) {
        return repository
                .findFirstByChainIdAndContractAddressAndTypeAndOwnerAddressAndIdempotencyKeyOrderByCreatedAtDesc(
                        deploymentId.chainId(),
                        deploymentId.contractAddress(),
                        CommandType.CREATE_BATCH,
                        ownerAddress,
                        idempotencyKey)
                .map(entity -> new BatchIdempotency(
                        entity.toDomain(), entity.requestHash()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> findConfirmedAcceptedStageIds(DeploymentId deploymentId, String batchId) {
        return repository
                .findByChainIdAndContractAddressAndBatchIdAndTypeAndStatusAndOutcome(
                        deploymentId.chainId(),
                        deploymentId.contractAddress(),
                        batchId,
                        CommandType.STAGE,
                        CommandStatus.CONFIRMED,
                        CommandOutcome.ACCEPTED)
                .stream()
                .map(StageCommandEntity::toDomain)
                .map(StageCommand::stageId)
                .toList();
    }

    private StageCommand persist(StageCommandEntity entity) {
        entityManager.persist(entity);
        try {
            entityManager.flush();
        } catch (DataIntegrityViolationException | PersistenceException exception) {
            throw new DuplicateCommandException("command conflicts with an existing row", exception);
        }
        return entity.toDomain();
    }
}
