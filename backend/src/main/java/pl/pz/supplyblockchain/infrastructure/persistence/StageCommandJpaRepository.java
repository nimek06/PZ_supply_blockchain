package pl.pz.supplyblockchain.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.pz.supplyblockchain.domain.CommandType;

interface StageCommandJpaRepository extends JpaRepository<StageCommandEntity, UUID> {

    Optional<StageCommandEntity> findByChainIdAndContractAddressAndSignedPayloadDigest(
            long chainId, String contractAddress, String signedPayloadDigest);

    Optional<StageCommandEntity> findFirstByChainIdAndContractAddressAndBatchIdAndStageIdAndTypeOrderByCreatedAtDesc(
            long chainId, String contractAddress, String batchId, Integer stageId, CommandType type);
}
