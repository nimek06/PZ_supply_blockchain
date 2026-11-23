package pl.pz.supplyblockchain.infrastructure.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import pl.pz.supplyblockchain.application.ports.BatchState;
import pl.pz.supplyblockchain.application.ports.ChainReceipt;
import pl.pz.supplyblockchain.application.ports.ChainUnavailableException;
import pl.pz.supplyblockchain.application.ports.CommandLeaseRepository;
import pl.pz.supplyblockchain.application.ports.CommandLifecycleRepository;
import pl.pz.supplyblockchain.application.ports.CommandPayloadRepository;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.application.ports.ConfirmedBlockTimeSource;
import pl.pz.supplyblockchain.application.ports.LeaseLostException;
import pl.pz.supplyblockchain.application.ports.RelayerChainGateway;
import pl.pz.supplyblockchain.application.ports.RelayerTransactionException;
import pl.pz.supplyblockchain.application.ports.ReportStorage;
import pl.pz.supplyblockchain.application.ports.ReportStorageException;
import pl.pz.supplyblockchain.application.ports.StationCertificateRepository;
import pl.pz.supplyblockchain.application.ports.StationCertificateVerifier;
import pl.pz.supplyblockchain.application.ports.StationRegistryException;
import pl.pz.supplyblockchain.application.ports.StoredReport;
import pl.pz.supplyblockchain.application.ports.SubmittedAttempt;
import pl.pz.supplyblockchain.application.ports.SubmittedRecoveryRepository;
import pl.pz.supplyblockchain.application.ports.TransactionAttemptRepository;
import pl.pz.supplyblockchain.application.ports.WorkerCommandPayload;
import pl.pz.supplyblockchain.config.RelayerWorkerProperties;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.StageCommand;
import pl.pz.supplyblockchain.domain.StationCertificate;
import pl.pz.supplyblockchain.domain.StationIdentityVerification;
import pl.pz.supplyblockchain.domain.TransactionAttempt;
import pl.pz.supplyblockchain.domain.TransactionReceiptStatus;
import pl.pz.supplyblockchain.infrastructure.blockchain.RelayerTransactionSender;

/**
 * Minimal single-instance relayer worker. Database transactions only cover lease/status/attempt
 * writes; Kubo and RPC calls happen after the transaction returns.
 */
public class RelayerWorker {

    private static final Logger LOG = LoggerFactory.getLogger(RelayerWorker.class);
    private static final int QA_STAGE = 2;

    private final CommandLeaseRepository leaseRepository;
    private final CommandLifecycleRepository lifecycleRepository;
    private final CommandPayloadRepository payloadRepository;
    private final CommandRepository commandRepository;
    private final SubmittedRecoveryRepository recoveryRepository;
    private final TransactionAttemptRepository attemptRepository;
    private final ReportStorage reportStorage;
    private final StationCertificateRepository certificateRepository;
    private final StationCertificateVerifier certificateVerifier;
    private final RelayerChainGateway chainGateway;
    private final ConfirmedBlockTimeSource confirmedBlockTime;
    private final RelayerTransactionSender sender;
    private final RelayerWorkerProperties properties;
    private final java.time.Clock clock;

    public RelayerWorker(
            CommandLeaseRepository leaseRepository,
            CommandLifecycleRepository lifecycleRepository,
            CommandPayloadRepository payloadRepository,
            CommandRepository commandRepository,
            SubmittedRecoveryRepository recoveryRepository,
            TransactionAttemptRepository attemptRepository,
            ReportStorage reportStorage,
            StationCertificateRepository certificateRepository,
            StationCertificateVerifier certificateVerifier,
            RelayerChainGateway chainGateway,
            ConfirmedBlockTimeSource confirmedBlockTime,
            RelayerTransactionSender sender,
            RelayerWorkerProperties properties,
            java.time.Clock clock) {
        this.leaseRepository = leaseRepository;
        this.lifecycleRepository = lifecycleRepository;
        this.payloadRepository = payloadRepository;
        this.commandRepository = commandRepository;
        this.recoveryRepository = recoveryRepository;
        this.attemptRepository = attemptRepository;
        this.reportStorage = reportStorage;
        this.certificateRepository = certificateRepository;
        this.certificateVerifier = certificateVerifier;
        this.chainGateway = chainGateway;
        this.confirmedBlockTime = confirmedBlockTime;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${supplychain.relayer.worker.poll-interval-ms:2000}")
    public void runOnce() {
        Instant now = clock.instant();
        try {
            recoverOne(now);
        } catch (RuntimeException exception) {
            LOG.error("relayer recovery tick failed", exception);
        }
        try {
            processOne(now);
        } catch (RuntimeException exception) {
            LOG.error("relayer queue tick failed", exception);
        }
    }

    private void recoverOne(Instant now) {
        Optional<SubmittedAttempt> claimed =
                recoveryRepository.claimNext(leaseOwner(), now, leaseDuration());
        if (claimed.isEmpty()) {
            return;
        }
        StageCommand command = claimed.get().command();
        try {
            reconcile(claimed.get(), now);
        } catch (LeaseLostException exception) {
            LOG.warn("lease lost while reconciling command {}", command.id());
        } catch (RuntimeException exception) {
            handleFailure(command.id(), exception, now);
        } finally {
            leaseRepository.release(command.id(), leaseOwner(), now);
        }
    }

    private void processOne(Instant now) {
        Optional<StageCommand> claimed =
                leaseRepository.acquireNext(leaseOwner(), now, leaseDuration());
        if (claimed.isEmpty()) {
            return;
        }
        StageCommand command = claimed.get();
        try {
            process(command, now);
        } catch (LeaseLostException exception) {
            LOG.warn("lease lost while processing command {}", command.id());
        } catch (RuntimeException exception) {
            handleFailure(command.id(), exception, now);
        } finally {
            leaseRepository.release(command.id(), leaseOwner(), now);
        }
    }

    private void process(StageCommand command, Instant now) {
        if (command.type() == CommandType.CREATE_BATCH) {
            processCreateBatch(command, now);
        } else if (command.type() == CommandType.STAGE) {
            processStage(command, now);
        }
    }

    private void processCreateBatch(StageCommand command, Instant now) {
        StageCommand current = command;
        if (current.status() == CommandStatus.ACCEPTED) {
            current = lifecycleRepository.transition(
                    current.id(), leaseOwner(), CommandStatus.ACCEPTED,
                    CommandStatus.READY_TO_SUBMIT, null, null, null, null, now);
        } else if (current.status() == CommandStatus.PINNING) {
            current = lifecycleRepository.transition(
                    current.id(), leaseOwner(), CommandStatus.PINNING,
                    CommandStatus.READY_TO_SUBMIT, null, null, null, null, now);
        }
        if (current.status() != CommandStatus.READY_TO_SUBMIT) {
            return;
        }
        if (current.batchNumber() == null || current.batchNumber().isBlank()) {
            throw permanent("BATCH_NUMBER_MISSING", "batch number is missing");
        }
        preflightCreateBatch(current);
        sender.send(current, null, leaseOwner(), now);
    }

    private void processStage(StageCommand command, Instant now) {
        StageCommand current = command;
        if (current.status() == CommandStatus.ACCEPTED) {
            current = lifecycleRepository.transition(
                    current.id(), leaseOwner(), CommandStatus.ACCEPTED,
                    CommandStatus.PINNING, null, null, null, null, now);
        }

        WorkerCommandPayload payload = payloadRepository.findById(current.id())
                .orElseThrow(() -> new RelayerTransactionException(
                        false, "PAYLOAD_MISSING", "stored report payload is missing", null));

        if (current.status() == CommandStatus.PINNING) {
            if (current.cid() == null || current.cid().isBlank()) {
                StoredReport stored = store(payload, current);
                CommandStatus target = needsQaApproval(current)
                        ? CommandStatus.WAITING_FOR_QA
                        : CommandStatus.READY_TO_SUBMIT;
                current = lifecycleRepository.transition(
                        current.id(), leaseOwner(), CommandStatus.PINNING, target,
                        stored.cid(), null, null, null, now);
            } else {
                CommandStatus target = needsQaApproval(current)
                        ? CommandStatus.WAITING_FOR_QA
                        : CommandStatus.READY_TO_SUBMIT;
                current = lifecycleRepository.transition(
                        current.id(), leaseOwner(), CommandStatus.PINNING, target,
                        null, null, null, null, now);
            }
            if (current.status() == CommandStatus.WAITING_FOR_QA) {
                return;
            }
        }

        if (current.status() == CommandStatus.READY_TO_SUBMIT && needsQaApproval(current)) {
            lifecycleRepository.transition(
                    current.id(), leaseOwner(), CommandStatus.READY_TO_SUBMIT,
                    CommandStatus.WAITING_FOR_QA, null, null, null, null, now);
            return;
        }

        if (current.status() == CommandStatus.READY_TO_SUBMIT) {
            preflightStage(current);
            if (payload.stationSignature() == null || payload.stationSignature().isBlank()) {
                throw permanent("STATION_SIGNATURE_MISSING", "station signature is missing");
            }
            if (current.cid() == null || current.cid().isBlank()) {
                throw permanent("CID_MISSING", "report CID is missing before send");
            }
            sender.send(current, payload.stationSignature(), leaseOwner(), now);
        }
    }

    private StoredReport store(WorkerCommandPayload payload, StageCommand command) {
        if (payload.reportJcs() == null || payload.reportJcs().length == 0) {
            throw new RelayerTransactionException(
                    false, "REPORT_MISSING", "canonical report bytes are missing", null);
        }
        try {
            return reportStorage.store(payload.reportJcs());
        } catch (ReportStorageException exception) {
            if (exception.reason().retryable()) {
                throw new RelayerTransactionException(
                        true, "REPORT_STORAGE_" + exception.reason().name(),
                        "report storage is temporarily unavailable", exception);
            }
            throw new RelayerTransactionException(
                    false, "REPORT_STORAGE_" + exception.reason().name(),
                    "report storage rejected the report", exception);
        }
    }

    private void preflightCreateBatch(StageCommand command) {
        if (command.batchId() == null || command.batchId().isBlank()) {
            throw permanent("BATCH_ID_MISSING", "batchId is missing");
        }
        BatchState state = chainGateway.getBatch(command.batchId());
        if (state.exists()) {
            throw permanent("BATCH_ALREADY_EXISTS", "batch already exists on-chain");
        }
    }

    private void preflightStage(StageCommand command) {
        if (command.batchId() == null || command.stageId() == null || command.deadline() == null
                || command.stationAddress() == null || command.stationCertificateId() == null
                || command.reportHash() == null) {
            throw permanent("STAGE_DATA_MISSING", "stage command is incomplete");
        }
        BatchState state = chainGateway.getBatch(command.batchId());
        if (!state.exists()) {
            throw permanent("BATCH_NOT_FOUND", "batch does not exist on-chain");
        }
        if (state.completed()) {
            throw permanent("BATCH_ALREADY_COMPLETED", "batch is already completed on-chain");
        }
        if (state.nextStage() != command.stageId()) {
            throw permanent("UNEXPECTED_STAGE", "on-chain nextStage differs from the command");
        }
        long confirmedNow = confirmedBlockTime.confirmedTimestamp();
        if (confirmedNow > command.deadline()) {
            throw permanent("DEADLINE_EXPIRED", "signed deadline has already passed");
        }

        StationCertificate certificate = certificateRepository
                .findById(command.stationCertificateId())
                .orElseThrow(() -> permanent(
                        "CERTIFICATE_INVALID", "station certificate is missing"));
        StationIdentityVerification verification = certificateVerifier.verifyForReport(
                certificate.certificateJson(), command.stationAddress(), command.stageId());
        if (!verification.accepted()) {
            throw permanent(certificateFailureCode(verification.reason()), "station is not active");
        }
    }

    private void reconcile(SubmittedAttempt claimed, Instant now) {
        StageCommand command = claimed.command();
        TransactionAttempt attempt = claimed.attempt();
        if (attempt.txHash() == null || attempt.txHash().isBlank() || attempt.signedTx() == null) {
            throw permanent("ATTEMPT_INVALID", "submitted attempt has no hash or signed bytes");
        }

        Optional<ChainReceipt> receipt = chainGateway.findReceipt(attempt.txHash());
        if (receipt.isPresent()) {
            TransactionAttempt updated = attempt.withReceipt(
                    receipt.get().blockNumber(), receipt.get().blockHash(), receipt.get().status());
            attemptRepository.update(updated);
            if (receipt.get().status() == TransactionReceiptStatus.REVERTED) {
                throw permanent("RECEIPT_REVERTED", "transaction receipt is REVERTED");
            }
            return;
        }

        String returnedHash = chainGateway.sendRawTransaction(attempt.signedTx());
        if (!attempt.txHash().equalsIgnoreCase(returnedHash)) {
            throw new RelayerTransactionException(
                    true, "TX_HASH_MISMATCH", "resend returned a different transaction hash", null);
        }
        attemptRepository.update(attempt.withBroadcastAt(now));
    }

    private void handleFailure(java.util.UUID commandId, RuntimeException exception, Instant now) {
        try {
            if (exception instanceof RelayerTransactionException relayer
                    && !relayer.retryable()) {
                fail(commandId, relayer.failureCode(), now);
            } else if (exception instanceof ChainUnavailableException
                    || exception instanceof StationRegistryException
                    || exception instanceof RelayerTransactionException) {
                retry(commandId, now);
            } else if (exception instanceof pl.pz.supplyblockchain.domain.DomainException) {
                fail(commandId, "COMMAND_INVALID", now);
            } else {
                LOG.error("unexpected relayer failure for command {}", commandId, exception);
                retry(commandId, now);
            }
        } catch (LeaseLostException leaseLost) {
            LOG.warn("lease lost while handling failure for command {}", commandId);
        }
    }

    private void retry(java.util.UUID commandId, Instant now) {
        StageCommand current = current(commandId);
        if (current == null || !retryableStatus(current.status())) {
            return;
        }
        lifecycleRepository.transition(
                commandId,
                leaseOwner(),
                current.status(),
                current.status(),
                null,
                null,
                null,
                now.plusSeconds(properties.retryBackoffSeconds()),
                now);
    }

    private void fail(java.util.UUID commandId, String failureCode, Instant now) {
        StageCommand current = current(commandId);
        if (current == null || current.status() == CommandStatus.CONFIRMED
                || current.status() == CommandStatus.FAILED) {
            return;
        }
        Long retryAllowedAfter = current.type() == CommandType.STAGE ? current.deadline() : null;
        lifecycleRepository.transition(
                commandId,
                leaseOwner(),
                current.status(),
                CommandStatus.FAILED,
                null,
                safeCode(failureCode),
                retryAllowedAfter,
                null,
                now);
    }

    private StageCommand current(java.util.UUID commandId) {
        return commandRepository.findById(commandId).orElse(null);
    }

    private static boolean retryableStatus(CommandStatus status) {
        return status == CommandStatus.PINNING
                || status == CommandStatus.READY_TO_SUBMIT
                || status == CommandStatus.SUBMITTED;
    }

    private static boolean needsQaApproval(StageCommand command) {
        return command.stageId() != null
                && command.stageId() == QA_STAGE
                && Boolean.TRUE.equals(command.qaPassed());
    }

    private static String certificateFailureCode(StationIdentityVerification.Reason reason) {
        return switch (reason) {
            case REVOKED, NOT_REGISTERED -> "STATION_NOT_ACTIVE";
            case REGISTRY_MISMATCH -> "REGISTRY_MISMATCH";
            case STAGE_NOT_ALLOWED -> "STAGE_NOT_ALLOWED";
            case SIGNER_MISMATCH -> "SIGNER_MISMATCH";
            default -> "CERTIFICATE_INVALID";
        };
    }

    private static RelayerTransactionException permanent(String code, String message) {
        return new RelayerTransactionException(false, code, message, null);
    }

    private static String safeCode(String value) {
        if (value == null || value.isBlank()) {
            return "RELAYER_FAILED";
        }
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private String leaseOwner() {
        return properties.effectiveLeaseOwner();
    }

    private Duration leaseDuration() {
        return Duration.ofSeconds(properties.leaseSeconds());
    }
}
