package pl.pz.supplyblockchain.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.pz.supplyblockchain.application.AuthService;
import pl.pz.supplyblockchain.application.SubmitStageService;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.StageCommand;

@WebMvcTest(StageController.class)
class StageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitStageService submitStageService;

    @MockitoBean
    private AuthService authService;

    private final String batchId = "0x79709e7401c7c94e8c6439dd2cd3729509c3c33d6b80b2df940e9c435df582f1";

    private final String validBody = """
            {
              "report": {
                "schemaVersion": 1,
                "batchId": "0x79709e7401c7c94e8c6439dd2cd3729509c3c33d6b80b2df940e9c435df582f1",
                "stageId": 2,
                "stationAddress": "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
                "timestamp": 1800000000,
                "measurements": { "temp": 21500 },
                "qaPassed": true
              },
              "stationCertificateId": "0xcert",
              "reportHash": "0x4707421ac9d17015745b8d29d2684f271eb6d37cc2a22f7f01456b88a894e97d",
              "deadline": 1800000600,
              "signature": "0xsig"
            }
            """;

    @Test
    void postStageReturns202WithLocationHeaderAndNoTxHash() throws Exception {
        UUID commandId = UUID.randomUUID();
        StageCommand command = new StageCommand(
                commandId, CommandType.STAGE, "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
                batchId, null, 2, "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
                "0xcert", "0x4707421ac9d17015745b8d29d2684f271eb6d37cc2a22f7f01456b88a894e97d",
                "0xdigest", 1800000600L, true, CommandStatus.ACCEPTED, null, null, null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(authService.authenticateOptional(any())).thenReturn(Optional.empty());
        when(submitStageService.submit(any())).thenReturn(command);

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/commands/" + commandId))
                .andExpect(jsonPath("$.commandId").value(commandId.toString()))
                .andExpect(jsonPath("$.type").value("STAGE"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.statusUrl").value("/api/v1/commands/" + commandId))
                .andExpect(jsonPath("$.txHash").doesNotExist());
    }

    @Test
    void postStageMapsDomainExceptionToProblemDetail() throws Exception {
        when(authService.authenticateOptional(any())).thenReturn(Optional.empty());
        when(submitStageService.submit(any()))
                .thenThrow(new DomainException(ErrorCode.STAGE_ALREADY_PENDING, "stage pending", 1800001000L));

        mockMvc.perform(post("/api/v1/batches/{batchId}/stages", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_ALREADY_PENDING"))
                .andExpect(jsonPath("$.retryAllowedAfter").value(1800001000L));
    }
}
