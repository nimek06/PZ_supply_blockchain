package pl.pz.supplyblockchain.application;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import pl.pz.supplyblockchain.application.batch.BatchView;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.BatchId;
import pl.pz.supplyblockchain.domain.BatchNumber;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.StageCommand;

/**
 * Read model for a batch. The indexer is not implemented yet, so only locally confirmed stage
 * commands are visible and the projection is always marked stale.
 */
@Service
public class BatchQueryService {

    private static final int FIRST_STAGE = 0;
    private static final int LAST_STAGE = 3;
    private static final long LAST_INDEXED_BLOCK = 0L;
    private static final long REQUIRED_CONFIRMATIONS = 1L;

    private final CommandRepository commandRepository;
    private final ChainProperties chainProperties;

    public BatchQueryService(CommandRepository commandRepository, ChainProperties chainProperties) {
        this.commandRepository = commandRepository;
        this.chainProperties = chainProperties;
    }

    public BatchView findByBatchId(String batchId) {
        String normalized = parseBatchId(batchId);
        StageCommand createCommand = commandRepository
                .findCreateBatch(chainProperties.deploymentId(), normalized)
                .orElseThrow(BatchQueryService::notFound);
        return toView(createCommand);
    }

    public BatchView findByBatchNumber(String batchNumber) {
        String normalized = parseBatchNumber(batchNumber);
        StageCommand createCommand = commandRepository
                .findCreateBatchByNumber(chainProperties.deploymentId(), normalized)
                .orElseThrow(BatchQueryService::notFound);
        return toView(createCommand);
    }

    private BatchView toView(StageCommand createCommand) {
        Set<Integer> completedStages = new HashSet<>(commandRepository.findConfirmedAcceptedStageIds(
                chainProperties.deploymentId(), createCommand.batchId()));
        boolean completed = completedStages.containsAll(
                Set.of(FIRST_STAGE, FIRST_STAGE + 1, FIRST_STAGE + 2, LAST_STAGE));
        Integer nextStage = null;
        if (!completed) {
            nextStage = FIRST_STAGE;
            while (completedStages.contains(nextStage)) {
                nextStage++;
            }
        }
        return new BatchView(
                createCommand.batchId(),
                createCommand.batchNumber(),
                completed,
                nextStage,
                new BatchView.ProjectionFreshness(
                        LAST_INDEXED_BLOCK, REQUIRED_CONFIRMATIONS, true));
    }

    private static String parseBatchId(String value) {
        try {
            return new BatchId(value).value();
        } catch (IllegalArgumentException exception) {
            throw new DomainException(ErrorCode.INVALID_REQUEST, "batchId must be a bytes32 hex value");
        }
    }

    private static String parseBatchNumber(String value) {
        try {
            return new BatchNumber(value).value();
        } catch (IllegalArgumentException exception) {
            throw new DomainException(ErrorCode.INVALID_REQUEST, "batchNumber must match B-RRRR-NNNN");
        }
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "batch not found");
    }
}
