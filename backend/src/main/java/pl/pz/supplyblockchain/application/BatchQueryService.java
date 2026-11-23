package pl.pz.supplyblockchain.application;

import org.springframework.stereotype.Service;
import pl.pz.supplyblockchain.application.batch.BatchView;
import pl.pz.supplyblockchain.application.ports.BatchProjection;
import pl.pz.supplyblockchain.application.ports.BatchProjectionRepository;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.BatchId;
import pl.pz.supplyblockchain.domain.BatchNumber;

@Service
public class BatchQueryService {

    private static final long REQUIRED_CONFIRMATIONS = 1L;

    private final BatchProjectionRepository projectionRepository;
    private final ChainProperties chainProperties;

    public BatchQueryService(
            BatchProjectionRepository projectionRepository, ChainProperties chainProperties) {
        this.projectionRepository = projectionRepository;
        this.chainProperties = chainProperties;
    }

    public BatchView findByBatchId(String batchId) {
        String normalized = parseBatchId(batchId);
        BatchProjection projection = projectionRepository
                .findByBatchId(chainProperties.deploymentId(), normalized)
                .orElseThrow(BatchQueryService::notFound);
        return toView(projection);
    }

    public BatchView findByBatchNumber(String batchNumber) {
        String normalized = parseBatchNumber(batchNumber);
        BatchProjection projection = projectionRepository
                .findByBatchNumber(chainProperties.deploymentId(), normalized)
                .orElseThrow(BatchQueryService::notFound);
        return toView(projection);
    }

    private static BatchView toView(BatchProjection projection) {
        return new BatchView(
                projection.batchId(),
                projection.batchNumber(),
                projection.completed(),
                projection.completed() ? null : projection.nextStage(),
                new BatchView.ProjectionFreshness(
                        projection.lastIndexedBlock(),
                        REQUIRED_CONFIRMATIONS,
                        projection.stale()));
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
