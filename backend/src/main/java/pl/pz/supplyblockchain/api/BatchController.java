package pl.pz.supplyblockchain.api;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pz.supplyblockchain.api.dto.BatchResponse;
import pl.pz.supplyblockchain.api.dto.CommandAcceptedResponse;
import pl.pz.supplyblockchain.api.dto.CreateBatchRequest;
import pl.pz.supplyblockchain.api.dto.ProjectionFreshnessResponse;
import pl.pz.supplyblockchain.application.AuthService;
import pl.pz.supplyblockchain.application.BatchQueryService;
import pl.pz.supplyblockchain.application.CreateBatchService;
import pl.pz.supplyblockchain.application.batch.BatchView;
import pl.pz.supplyblockchain.domain.StageCommand;

@RestController
@RequestMapping("/api/v1")
public class BatchController {

    private final CreateBatchService createBatchService;
    private final BatchQueryService batchQueryService;
    private final AuthService authService;

    public BatchController(
            CreateBatchService createBatchService,
            BatchQueryService batchQueryService,
            AuthService authService) {
        this.createBatchService = createBatchService;
        this.batchQueryService = batchQueryService;
        this.authService = authService;
    }

    @PostMapping("/batches")
    public ResponseEntity<CommandAcceptedResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CreateBatchRequest request) {
        String owner = authService.authenticateRequired(authorization);
        StageCommand accepted = createBatchService.create(owner, idempotencyKey);
        String statusUrl = "/api/v1/commands/" + accepted.id();
        return ResponseEntity.accepted()
                .location(URI.create(statusUrl))
                .body(new CommandAcceptedResponse(
                        accepted.id(),
                        accepted.type().name(),
                        accepted.status().name(),
                        statusUrl,
                        accepted.batchId(),
                        accepted.batchNumber()));
    }

    @GetMapping("/batches/{batchId}")
    public BatchResponse getByBatchId(@PathVariable String batchId) {
        return toResponse(batchQueryService.findByBatchId(batchId));
    }

    @GetMapping("/batch-numbers/{batchNumber}")
    public BatchResponse getByBatchNumber(@PathVariable String batchNumber) {
        return toResponse(batchQueryService.findByBatchNumber(batchNumber));
    }

    private static BatchResponse toResponse(BatchView view) {
        BatchView.ProjectionFreshness projection = view.projection();
        return new BatchResponse(
                view.batchId(),
                view.batchNumber(),
                view.completed(),
                view.nextStage(),
                new ProjectionFreshnessResponse(
                        projection.lastIndexedBlock(),
                        projection.requiredConfirmations(),
                        projection.stale()));
    }
}
