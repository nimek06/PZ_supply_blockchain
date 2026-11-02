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
import pl.pz.supplyblockchain.application.ports.DuplicateCommandException;
import pl.pz.supplyblockchain.application.ports.NewStageCommand;
import pl.pz.supplyblockchain.application.ports.ReportCanonicalizer;
import pl.pz.supplyblockchain.application.ports.StageCompletionVerifier;
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
import pl.pz.supplyblockchain.domain.TelemetryReport;

@Service
public class SubmitStageService {

    private static final Pattern BYTES32 = Pattern.compile("^0x[0-9a-fA-F]{64}$");

    private final CommandRepository commandRepository;
    private final ReportCanonicalizer reportCanonicalizer;
    private final StageCompletionVerifier signatureVerifier;
    private final ChainProperties chainProperties;
    private final Clock clock;

    public SubmitStageService(
            CommandRepository commandRepository,
            ReportCanonicalizer reportCanonicalizer,
            StageCompletionVerifier signatureVerifier,
            ChainProperties chainProperties,
            Clock clock) {
        this.commandRepository = commandRepository;
        this.reportCanonicalizer = reportCanonicalizer;
        this.signatureVerifier = signatureVerifier;
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

        long now = clock.instant().getEpochSecond();
        if (request.deadline() < now) {
            throw new DomainException(ErrorCode.DEADLINE_EXPIRED, "deadline has already passed");
        }
        if (request.deadline() > now + chainProperties.maxDeadlineWindowSeconds()) {
            throw new DomainException(ErrorCode.DEADLINE_TOO_FAR,
                    "deadline is further than the configured limit from now");
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

        DeploymentId deploymentId = chainProperties.deploymentId();
        Optional<StageCommand> sameDigest =
                commandRepository.findBySignedPayloadDigest(deploymentId, verification.digest());
        if (sameDigest.isPresent()) {
            return sameDigest.get();
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
            if (latest.outcome() == CommandOutcome.ACCEPTED) {
                throw new DomainException(ErrorCode.STAGE_ALREADY_COMPLETED,
                        "stage has already been completed on-chain");
            }
            return;
        }
        if (latest.status() == CommandStatus.FAILED) {
            long retryAfter = latest.retryAllowedAfter() == null ? latest.deadline() : latest.retryAllowedAfter();
            if (now <= retryAfter) {
                throw new DomainException(ErrorCode.STAGE_ALREADY_PENDING,
                        "the previous signed attempt has not expired yet", retryAfter);
            }
            return;
        }
        throw new DomainException(ErrorCode.STAGE_ALREADY_PENDING,
                "stage already has an active command", latest.retryAllowedAfter());
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
