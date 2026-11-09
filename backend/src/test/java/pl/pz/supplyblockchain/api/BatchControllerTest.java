package pl.pz.supplyblockchain.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.pz.supplyblockchain.application.AuthService;
import pl.pz.supplyblockchain.application.BatchQueryService;
import pl.pz.supplyblockchain.application.CreateBatchService;
import pl.pz.supplyblockchain.application.batch.BatchView;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.StageCommand;

@WebMvcTest(BatchController.class)
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateBatchService createBatchService;

    @MockitoBean
    private BatchQueryService batchQueryService;

    @MockitoBean
    private AuthService authService;

    private final String operator = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private final String batchId = "0xbd23cd342e8d6764ba34c70b9da72967ec708e76f1acf807f196c4557b5e635b";
    private final String batchNumber = "B-2026-0001";

    @Test
    void createBatchReturns202WithBatchDetails() throws Exception {
        UUID commandId = UUID.randomUUID();
        StageCommand accepted = new StageCommand(
                commandId, CommandType.CREATE_BATCH, operator, batchId, batchNumber,
                null, null, null, null, null, null, null,
                CommandStatus.ACCEPTED, null, null, null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(authService.authenticateRequired(any())).thenReturn(operator);
        when(createBatchService.create(any(), any())).thenReturn(accepted);

        mockMvc.perform(post("/api/v1/batches")
                        .header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "test-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/commands/" + commandId))
                .andExpect(jsonPath("$.commandId").value(commandId.toString()))
                .andExpect(jsonPath("$.type").value("CREATE_BATCH"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.statusUrl").value("/api/v1/commands/" + commandId))
                .andExpect(jsonPath("$.batchId").value(batchId))
                .andExpect(jsonPath("$.batchNumber").value(batchNumber));
    }

    @Test
    void createBatchWithoutIdempotencyKeyReturns400() throws Exception {
        when(authService.authenticateRequired(any())).thenReturn(operator);
        when(createBatchService.create(any(), any())).thenThrow(
                new DomainException(ErrorCode.INVALID_REQUEST, "Idempotency-Key header is required"));

        mockMvc.perform(post("/api/v1/batches")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void createBatchWithoutAuthReturns401() throws Exception {
        when(authService.authenticateRequired(any())).thenThrow(
                new DomainException(ErrorCode.UNAUTHENTICATED, "bearer token is required"));

        mockMvc.perform(post("/api/v1/batches")
                        .header("Idempotency-Key", "test-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void idempotentCreateReturnsSameCommandId() throws Exception {
        UUID commandId = UUID.randomUUID();
        StageCommand accepted = new StageCommand(
                commandId, CommandType.CREATE_BATCH, operator, batchId, batchNumber,
                null, null, null, null, null, null, null,
                CommandStatus.ACCEPTED, null, null, null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(authService.authenticateRequired(any())).thenReturn(operator);
        when(createBatchService.create(any(), any())).thenReturn(accepted);

        String path = "/api/v1/batches";
        String body = "{}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(path)
                            .header("Authorization", "Bearer token")
                            .header("Idempotency-Key", "same-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.commandId").value(commandId.toString()));
        }
    }

    @Test
    void getBatchByIdReturnsProjection() throws Exception {
        BatchView view = new BatchView(
                batchId,
                batchNumber,
                false,
                0,
                new BatchView.ProjectionFreshness(0L, 1L, true));

        when(batchQueryService.findByBatchId(batchId)).thenReturn(view);

        mockMvc.perform(get("/api/v1/batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId))
                .andExpect(jsonPath("$.batchNumber").value(batchNumber))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.nextStage").value(0))
                .andExpect(jsonPath("$.projection.lastIndexedBlock").value(0))
                .andExpect(jsonPath("$.projection.requiredConfirmations").value(1))
                .andExpect(jsonPath("$.projection.stale").value(true));
    }
}
