package pl.pz.supplyblockchain.api;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pz.supplyblockchain.api.dto.CommandStatusResponse;
import pl.pz.supplyblockchain.application.AuthService;
import pl.pz.supplyblockchain.application.CommandQueryService;
import pl.pz.supplyblockchain.domain.StageCommand;

@RestController
@RequestMapping("/api/v1/commands")
public class CommandController {

    private final CommandQueryService commandQueryService;
    private final AuthService authService;

    public CommandController(CommandQueryService commandQueryService, AuthService authService) {
        this.commandQueryService = commandQueryService;
        this.authService = authService;
    }

    @GetMapping("/{commandId}")
    public CommandStatusResponse get(
            @PathVariable UUID commandId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String owner = authService.authenticateRequired(authorization);
        StageCommand command = commandQueryService.getVisible(commandId, owner);
        return new CommandStatusResponse(
                command.id(),
                command.type().name(),
                command.status().name(),
                command.outcome() == null ? null : command.outcome().name(),
                command.batchId(),
                command.batchNumber(),
                command.stageId(),
                command.reportHash(),
                command.cid(),
                null,
                command.failureCode(),
                epochSeconds(command.retryAt()),
                command.retryAllowedAfter(),
                command.deadline(),
                command.createdAt().getEpochSecond(),
                command.updatedAt().getEpochSecond());
    }

    private static Long epochSeconds(java.time.Instant value) {
        return value == null ? null : value.getEpochSecond();
    }
}
