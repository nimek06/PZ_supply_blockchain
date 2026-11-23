package pl.pz.supplyblockchain.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.pz.supplyblockchain.application.AuthService;
import pl.pz.supplyblockchain.application.CommandQueryService;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.StageCommand;

@WebMvcTest(CommandController.class)
class CommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommandQueryService commandQueryService;

    @MockitoBean
    private AuthService authService;

    private final String owner = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

    @Test
    void missingTokenReturns401() throws Exception {
        when(authService.authenticateRequired(isNull())).thenThrow(
                new DomainException(ErrorCode.UNAUTHENTICATED, "bearer token is required"));

        mockMvc.perform(get("/api/v1/commands/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void foreignCommandReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.authenticateRequired(any())).thenReturn(owner);
        when(commandQueryService.getVisible(id, owner)).thenThrow(
                new DomainException(ErrorCode.NOT_FOUND, "command not found"));

        mockMvc.perform(get("/api/v1/commands/{id}", id)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void administratorReadsAnyCommand() throws Exception {
        UUID id = UUID.randomUUID();
        String administrator = "0x3c44cdd05a9000f507687041266870c569253590";
        StageCommand command = new StageCommand(
                id, CommandType.STAGE, owner, "0xbatch", null, 1, owner,
                "0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e",
                "0xhash", "0xdigest", 1800000600L, true, CommandStatus.SUBMITTED, null,
                null, null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(authService.authenticateRequired(any())).thenReturn(administrator);
        when(commandQueryService.getVisible(id, administrator)).thenReturn(command);

        mockMvc.perform(get("/api/v1/commands/{id}", id)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void ownerReadsCommandStatus() throws Exception {
        UUID id = UUID.randomUUID();
        StageCommand command = new StageCommand(
                id, CommandType.STAGE, owner, "0xbatch", null, 1, owner,
                "0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e",
                "0xhash", "0xdigest", 1800000600L, true, CommandStatus.ACCEPTED, null,
                null, null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(authService.authenticateRequired(any())).thenReturn(owner);
        when(commandQueryService.getVisible(id, owner)).thenReturn(command);

        mockMvc.perform(get("/api/v1/commands/{id}", id)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.outcome").doesNotExist());
    }
}
