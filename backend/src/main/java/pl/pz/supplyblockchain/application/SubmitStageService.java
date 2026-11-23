package pl.pz.supplyblockchain.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import pl.pz.supplyblockchain.application.commands.SubmitStageCommand;
import pl.pz.supplyblockchain.application.ports.CanonicalReport;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.application.ports.ConfirmedBlockTimeSource;
import pl.pz.supplyblockchain.application.ports.DuplicateCommandException;
import pl.pz.supplyblockchain.application.ports.NewStageCommand;
import pl.pz.supplyblockchain.application.ports.ReportCanonicalizer;
import pl.pz.supplyblockchain.application.ports.StageCompletionVerifier;
import pl.pz.supplyblockchain.application.ports.StationCertificateRepository;
import pl.pz.supplyblockchain.application.ports.StationCertificateVerifier;
import pl.pz.supplyblockchain.application.ports.TransactionAttemptRepository;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.CommandOutcome;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.SignatureVerification;
import pl.pz.supplyblockchain.domain.StageCommand;
import pl.pz.supplyblockchain.domain.StageCompletionMessage;
import pl.pz.supplyblockchain.domain.StationCertificate;
import pl.pz.supplyblockchain.domain.StationIdentityVerification;
import pl.pz.supplyblockchain.domain.TelemetryReport;

@Service
public class SubmitStageService {

    private static final Pattern BYTES32 = Pattern.compile("^0x[0-9a-fA-F]{64}$");

    private final CommandRepository commandRepository;
    private final ReportCanonicalizer reportCanonicalizer;
    private final StageCompletionVerifier signatureVerifier;
    private final ProfileValidator profileValidator;
    private final StationCertificateRepository certificateRepository;
    private final StationCertificateVerifier certificateVerifier;
    private final TransactionAttemptRepository attemptRepository;
    private final ConfirmedBlockTimeSource confirmedBlockTime;
    private final ChainProperties chainProperties;
    private final Clock clock;

    public SubmitStageService(
            CommandRepository commandRepository,
            ReportCanonicalizer reportCanonicalizer,
            StageCompletionVerifier signatureVerifier,
            ProfileValidator profileValidator,
            StationCertificateRepository certificateRepository,
            StationCertificateVerifier certificateVerifier,
            TransactionAttemptRepository attemptRepository,
            ConfirmedBlockTimeSource confirmedBlockTime,
            ChainProperties chainProperties,
            Clock clock) {
        this.commandRepository = commandRepository;
        this.reportCanonicalizer = reportCanonicalizer;
        this.signatureVerifier = signatureVerifier;
        this.profileValidator = profileValidator;
        this.certificateRepository = certificateRepository;
        this.certificateVerifier = certificateVerifier;
        this.attemptRepository = attemptRepository;
        this.confirmedBlockTime = confirmedBlockTime;
        this.chainProperties = chainProperties;
        this.clock = clock;
    }

    public StageCommand submit(SubmitStageCommand request) {
        validateRequest(request);
        TelemetryReport report = request.report();
        if (!report.normalizedBatchId().equalsIgnoreCase(request.pathBatchId())) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION,
                    "report.batchId does not match the path batchId");
        }

        CanonicalReport canonicalReport = reportCanonicalizer.canonicalize(report);
        if (!request.reportHash().equalsIgnoreCase(canonicalReport.reportHash())) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION,
                    "reportHash does not match the canonical report bytes");
        }

        SignatureVerification verification = signatureVerifier.verifyCompletion(
                new StageCompletionMessage(
                        report.normalizedBatchId(),
                        report.stageId(),
                        canonicalReport.reportHash(),
                        report.qaPassed(),
                        request.deadline()),
                request.signature());

        if (!verification.signerAddress().equalsIgnoreCase(report.stationAddress())) {
            throw new DomainException(ErrorCode.SIGNER_MISMATCH,
                    "signature was not produced by report.stationAddress");
        }

        request.authenticatedAddress().ifPresent(address -> {
            if (!address.equalsIgnoreCase(report.stationAddress())) {
                throw new DomainException(ErrorCode.SIGNER_MISMATCH,
                        "authenticated session does not belong to report.stationAddress");
            }
        });

        profileValidator.validate(report);

        StationCertificate certificate = certificateRepository
                .findById(request.stationCertificateId())
                .orElseThrow(() -> new DomainException(
                        ErrorCode.CERTIFICATE_INVALID, "station certificate not found"));
        StationIdentityVerification certVerification = certificateVerifier.verifyForReport(
                certificate.certificateJson(),
                report.stationAddress(),
                report.stageId());
        if (!certVerification.accepted()) {
            throw new DomainException(
                    mapCertificateReason(certVerification.reason()),
                    certVerification.detail());
        }

        // Replay is looked up after the current station proof, but before deadline/stage checks:
        // a revoked or unavailable station must not receive a command id from an old signature.
        DeploymentId deploymentId = chainProperties.deploymentId();
        Optional<StageCommand> sameDigest =
                commandRepository.findBySignedPayloadDigest(deploymentId, verification.digest());
        if (sameDigest.isPresent()) {
            return sameDigest.get();
        }

        long now = clock.instant().getEpochSecond();
        if (request.deadline() < now) {
            throw new DomainException(ErrorCode.DEADLINE_EXPIRED, "deadline has already passed");
        }
        if (request.deadline() > now + chainProperties.maxDeadlineWindowSeconds()) {
            throw new DomainException(ErrorCode.DEADLINE_TOO_FAR,
                    "deadline is further than the configured limit from now");
        }

        Optional<StageCommand> latest =
                commandRepository.findLatestStageCommand(deploymentId, report.normalizedBatchId(), report.stageId());
        if (latest.isPresent()) {
            checkReplacement(latest.get(), now, verification.digest());
        }

        StageCommand command = new StageCommand(
                UUID.randomUUID(),
                CommandType.STAGE,
                verification.signerAddress().toLowerCase(Locale.ROOT),
                report.normalizedBatchId(),
                null,
                report.stageId(),
                report.stationAddress(),
                request.stationCertificateId(),
                canonicalReport.reportHash(),
                verification.digest(),
                request.deadline(),
                report.qaPassed(),
                CommandStatus.ACCEPTED,
                null,
                null,
                null,
                null,
                null,
                Instant.ofEpochSecond(now),
                Instant.ofEpochSecond(now));

        try {
            return commandRepository.saveNew(new NewStageCommand(
                    deploymentId,
                    command,
                    canonicalReport.jcsBytes(),
                    request.signature(),
                    new String(canonicalReport.jcsBytes(), StandardCharsets.UTF_8)));
        } catch (DuplicateCommandException conflict) {
            return resolveConflict(deploymentId, report, verification.digest(), now);
        }
    }

    private StageCommand resolveConflict(DeploymentId deploymentId, TelemetryReport report, String digest, long now) {
        Optional<StageCommand> sameDigest = commandRepository.findBySignedPayloadDigest(deploymentId, digest);
        if (sameDigest.isPresent()) {
            return sameDigest.get();
        }
        Optional<StageCommand> latest =
                commandRepository.findLatestStageCommand(deploymentId, report.normalizedBatchId(), report.stageId());
        if (latest.isPresent()) {
            checkReplacement(latest.get(), now, digest);
            throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "a competing command was accepted; retry the request");
        }
        throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT,
                "command could not be accepted because of a concurrent request");
    }

    private void checkReplacement(StageCommand latest, long now, String digest) {
        if (latest.signedPayloadDigest().equalsIgnoreCase(digest)) {
            return;
        }
        if (latest.status() == CommandStatus.CONFIRMED) {
            if (latest.outcome() == CommandOutcome.REJECTED) {
                return;
            }
            throw new DomainException(ErrorCode.STAGE_ALREADY_COMPLETED,
                    latest.outcome() == CommandOutcome.ACCEPTED
                            ? "stage has already been completed on-chain"
                            : "confirmed command has no recognized outcome");
        }
        if (latest.status() == CommandStatus.FAILED) {
            long retryAfter = replacementThreshold(latest);
            if (attemptRepository.hasUnresolvedAttempts(latest.id())) {
                throw new DomainException(ErrorCode.STAGE_ALREADY_PENDING,
                        "the previous command has unresolved transaction attempts", retryAfter);
            }
            // A rejected QA report never moves the batch, so its signature cannot block the stage.
            if (Boolean.FALSE.equals(latest.qaPassed())) {
                return;
            }
            long confirmedNow = confirmedBlockTime.confirmedTimestamp();
            if (confirmedNow <= retryAfter) {
                throw new DomainException(ErrorCode.STAGE_ALREADY_PENDING,
                        "the confirmed block has not passed the previous deadline yet", retryAfter);
            }
            return;
        }
        throw new DomainException(ErrorCode.STAGE_ALREADY_PENDING,
                "stage already has an active command", latest.retryAllowedAfter());
    }

    /**
     * The old signature is unusable only after the confirmed chain time passes its deadline.
     * {@code retryAllowedAfter} may raise the threshold but never lowers the signed deadline.
     */
    private static long replacementThreshold(StageCommand command) {
        long deadline = command.deadline();
        Long retryAllowedAfter = command.retryAllowedAfter();
        return retryAllowedAfter == null ? deadline : Math.max(deadline, retryAllowedAfter);
    }

    private static ErrorCode mapCertificateReason(StationIdentityVerification.Reason reason) {
        return switch (reason) {
            case MALFORMED, PAYLOAD_HASH_MISMATCH, INVALID_ISSUER_SIGNATURE,
                    UNTRUSTED_ISSUER, WRONG_DEPLOYMENT, NOT_YET_VALID, EXPIRED ->
                    ErrorCode.CERTIFICATE_INVALID;
            case NOT_REGISTERED, REVOKED -> ErrorCode.STATION_NOT_ACTIVE;
            case REGISTRY_MISMATCH -> ErrorCode.REGISTRY_MISMATCH;
            case SIGNER_MISMATCH -> ErrorCode.SIGNER_MISMATCH;
            case STAGE_NOT_ALLOWED -> ErrorCode.STAGE_NOT_ALLOWED;
            case OK -> throw new IllegalStateException("accepted certificate has no failure reason");
        };
    }

    private static void validateRequest(SubmitStageCommand request) {
        if (request.pathBatchId() == null || request.pathBatchId().isBlank()) {
            throw new DomainException(ErrorCode.INVALID_REQUEST, "batchId path variable is required");
        }
        if (request.report() == null) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "report is required");
        }
        if (request.stationCertificateId() == null || !BYTES32.matcher(request.stationCertificateId()).matches()) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION,
                    "stationCertificateId must be a bytes32 hex value");
        }
        if (request.reportHash() == null || !BYTES32.matcher(request.reportHash()).matches()) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "reportHash must be a bytes32 hex value");
        }
        if (request.signature() == null || request.signature().isBlank()) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "signature is required");
        }
    }
}
