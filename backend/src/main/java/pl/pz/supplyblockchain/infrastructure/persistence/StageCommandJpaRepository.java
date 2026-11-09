package pl.pz.supplyblockchain.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.pz.supplyblockchain.domain.CommandOutcome;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;

interface StageCommandJpaRepository extends JpaRepository<StageCommandEntity, UUID> {

    Optional<StageCommandEntity> findByChainIdAndContractAddressAndSignedPayloadDigest(
            long chainId, String contractAddress, String signedPayloadDigest);

    Optional<StageCommandEntity> findFirstByChainIdAndContractAddressAndBatchIdAndStageIdAndTypeOrderByCreatedAtDesc(
            long chainId, String contractAddress, String batchId, Integer stageId, CommandType type);

    Optional<StageCommandEntity> findFirstByChainIdAndContractAddressAndTypeAndBatchIdOrderByCreatedAtDesc(
            long chainId, String contractAddress, CommandType type, String batchId);

    Optional<StageCommandEntity> findFirstByChainIdAndContractAddressAndTypeAndBatchNumberOrderByCreatedAtDesc(
            long chainId, String contractAddress, CommandType type, String batchNumber);

    Optional<StageCommandEntity> findFirstByChainIdAndContractAddressAndTypeAndOwnerAddressAndIdempotencyKeyOrderByCreatedAtDesc(
            long chainId,
            String contractAddress,
            CommandType type,
            String ownerAddress,
            String idempotencyKey);

    List<StageCommandEntity> findByChainIdAndContractAddressAndBatchIdAndTypeAndStatusAndOutcome(
            long chainId,
            String contractAddress,
            String batchId,
            CommandType type,
            CommandStatus status,
            CommandOutcome outcome);
}
