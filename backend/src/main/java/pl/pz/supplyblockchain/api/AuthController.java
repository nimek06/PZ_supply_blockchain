package pl.pz.supplyblockchain.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pz.supplyblockchain.api.dto.AuthChallengeRequest;
import pl.pz.supplyblockchain.api.dto.AuthChallengeResponse;
import pl.pz.supplyblockchain.api.dto.AuthSessionRequest;
import pl.pz.supplyblockchain.api.dto.AuthSessionResponse;
import pl.pz.supplyblockchain.application.AuthService;
import pl.pz.supplyblockchain.application.auth.NewAuthSession;
import pl.pz.supplyblockchain.domain.AuthChallenge;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/challenges")
    public ResponseEntity<AuthChallengeResponse> challenge(@RequestBody AuthChallengeRequest request) {
        if (request == null || request.address() == null || request.address().isBlank()) {
            throw new DomainException(ErrorCode.INVALID_REQUEST, "address is required");
        }
        AuthChallenge challenge = authService.createChallenge(request.address());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AuthChallengeResponse(
                        challenge.id(),
                        challenge.message(),
                        challenge.expiresAt().getEpochSecond()));
    }

    @PostMapping("/sessions")
    public ResponseEntity<AuthSessionResponse> session(@RequestBody AuthSessionRequest request) {
        if (request == null
                || request.challengeId() == null
                || request.message() == null
                || request.signature() == null) {
            throw new DomainException(ErrorCode.INVALID_REQUEST,
                    "challengeId, message and signature are required");
        }
        NewAuthSession created = authService.createSession(
                request.challengeId(), request.message(), request.signature());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AuthSessionResponse(
                        created.token(),
                        "Bearer",
                        created.session().address(),
                        created.session().expiresAt().getEpochSecond(),
                        created.session().idleTimeoutSeconds()));
    }

    @DeleteMapping("/sessions/current")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.revoke(authorization);
        return ResponseEntity.noContent().build();
    }
}
