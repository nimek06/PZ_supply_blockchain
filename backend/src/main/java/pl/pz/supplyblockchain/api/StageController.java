package pl.pz.supplyblockchain.api;

import java.net.URI;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pz.supplyblockchain.api.dto.CommandAcceptedResponse;
import pl.pz.supplyblockchain.api.dto.StageSubmissionMapper;
import pl.pz.supplyblockchain.api.dto.StageSubmissionRequest;
import pl.pz.supplyblockchain.application.AuthService;
import pl.pz.supplyblockchain.application.SubmitStageService;
import pl.pz.supplyblockchain.application.commands.SubmitStageCommand;
import pl.pz.supplyblockchain.domain.StageCommand;

@RestController
@RequestMapping("/api/v1/batches")
public class StageController {

    private final SubmitStageService submitStageService;
    private final AuthService authService;

    public StageController(SubmitStageService submitStageService, AuthService authService) {
        this.submitStageService = submitStageService;
        this.authService = authService;
    }

    @PostMapping("/{batchId}/stages")
    public ResponseEntity<CommandAcceptedResponse> submit(
            @PathVariable String batchId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody StageSubmissionRequest request) {
        Optional<String> authenticatedAddress = authService.authenticateOptional(authorization);
        SubmitStageCommand command = StageSubmissionMapper.toCommand(batchId, request, authenticatedAddress);
        StageCommand accepted = submitStageService.submit(command);
        String statusUrl = "/api/v1/commands/" + accepted.id();
        return ResponseEntity.accepted()
                .location(URI.create(statusUrl))
                .body(new CommandAcceptedResponse(
                        accepted.id(),
                        accepted.type().name(),
                        accepted.status().name(),
                        statusUrl));
    }
}
