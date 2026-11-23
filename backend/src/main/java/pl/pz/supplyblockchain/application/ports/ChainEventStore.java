package pl.pz.supplyblockchain.application.ports;

import java.util.List;
import java.util.Optional;
import pl.pz.supplyblockchain.domain.DeploymentId;

public interface ChainEventStore {

    Optional<IndexerCheckpoint> findCheckpoint(DeploymentId deploymentId);

    List<BlockAnchor> findRecentAnchors(DeploymentId deploymentId, int limit);

    void rewind(DeploymentId deploymentId, long blockNumber, String blockHash);

    void applyRange(
            DeploymentId deploymentId,
            List<ChainEvent> events,
            long scannedBlockNumber,
            String scannedBlockHash,
            List<BlockAnchor> anchors);
}
