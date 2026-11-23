package pl.pz.supplyblockchain.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import pl.pz.supplyblockchain.application.ports.ReportStorage;
import pl.pz.supplyblockchain.application.ports.ReportStorageException;
import pl.pz.supplyblockchain.application.ports.StationCertificateRepository;
import pl.pz.supplyblockchain.application.ports.StationCertificateVerifier;
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

class RelayerWorkerTest {

    private static final String OWNER = "worker-1";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String CERTIFICATE = "0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final byte[] JCS = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

    private final CommandLeaseRepository leases = mock(CommandLeaseRepository.class);
    private final CommandPayloadRepository payloads = mock(CommandPayloadRepository.class);
    private final CommandRepository commands = mock(CommandRepository.class);
    private final SubmittedRecoveryRepository recovery = mock(SubmittedRecoveryRepository.class);
    private final TransactionAttemptRepository attempts = mock(TransactionAttemptRepository.class);
    private final ReportStorage storage = mock(ReportStorage.class);
    private final StationCertificateRepository certificates = mock(StationCertificateRepository.class);
    private final StationCertificateVerifier verifier = mock(StationCertificateVerifier.class);
    private final RelayerChainGateway gateway = mock(RelayerChainGateway.class);
    private final ConfirmedBlockTimeSource confirmedTime = mock(ConfirmedBlockTimeSource.class);
    private final RelayerTransactionSender sender = mock(RelayerTransactionSender.class);
    private final FakeLifecycle lifecycle = new FakeLifecycle();
    private final RelayerWorkerProperties properties =
            new RelayerWorkerProperties(true, 2000, 30, 5, 120, OWNER);

    private RelayerWorker worker;

    @BeforeEach
    void setUp() {
        worker = new RelayerWorker(
                leases, lifecycle, payloads, commands, recovery, attempts, storage, certificates,
                verifier, gateway, confirmedTime, sender, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(commands.findById(any())).thenAnswer(
                invocation -> Optional.ofNullable(lifecycle.current(invocation.getArgument(0))));
        when(recovery.claimNext(anyString(), any(), any())).thenReturn(Optional.empty());
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.empty());
        when(certificates.findById(anyString())).thenReturn(
                Optional.of(new StationCertificate(CERTIFICATE, STATION, "{}", NOW)));
        when(verifier.verifyForReport(anyString(), anyString(), any(Integer.class))).thenReturn(
                new StationIdentityVerification(
                        StationIdentityVerification.Reason.OK, "ok", null, null, null));
        when(confirmedTime.confirmedTimestamp()).thenReturn(1800000000L);
        when(storage.store(any())).thenReturn(new StoredReport("bafy-test", "kubo"));
    }

    @Test
    void createBatchSkipsReportStorageAndSendsReadyCommand() {
        StageCommand command = command(CommandType.CREATE_BATCH, CommandStatus.ACCEPTED, 0, null, null);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(gateway.getBatch(anyString())).thenReturn(new BatchState(false, 0, false));

        worker.runOnce();

        verify(storage, never()).store(any());
        verify(sender).send(
                org.mockito.ArgumentMatchers.argThat(value ->
                        value.status() == CommandStatus.READY_TO_SUBMIT
                                && value.type() == CommandType.CREATE_BATCH),
                eq(null), eq(OWNER), eq(NOW));
        assertThat(lifecycle.current(command.id()).status()).isEqualTo(CommandStatus.READY_TO_SUBMIT);
    }

    @Test
    void stagePinsExactlyTheStoredJcsAndBecomesReady() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.ACCEPTED, 0, null, true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(gateway.getBatch(anyString())).thenReturn(new BatchState(true, 0, false));

        worker.runOnce();

        verify(storage).store(org.mockito.ArgumentMatchers.same(JCS));
        verify(sender).send(
                org.mockito.ArgumentMatchers.argThat(value ->
                        value.status() == CommandStatus.READY_TO_SUBMIT
                                && "bafy-test".equals(value.cid())),
                eq("0xsig"), eq(OWNER), eq(NOW));
        assertThat(lifecycle.current(command.id()).status()).isEqualTo(CommandStatus.READY_TO_SUBMIT);
        assertThat(lifecycle.current(command.id()).cid()).isEqualTo("bafy-test");
    }

    @Test
    void storedCidIsNotPinnedAgain() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.PINNING, 0, "bafy-existing", true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(gateway.getBatch(anyString())).thenReturn(new BatchState(true, 0, false));

        worker.runOnce();

        verify(storage, never()).store(any());
        verify(sender).send(
                org.mockito.ArgumentMatchers.argThat(value ->
                        value.status() == CommandStatus.READY_TO_SUBMIT
                                && "bafy-existing".equals(value.cid())),
                eq("0xsig"), eq(OWNER), eq(NOW));
    }

    @Test
    void positiveQaStageWaitsWithoutReservingNonceOrSending() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.ACCEPTED, 2, null, true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));

        worker.runOnce();

        verify(storage).store(org.mockito.ArgumentMatchers.same(JCS));
        verify(sender, never()).send(any(), any(), any(), any());
        verify(gateway, never()).getBatch(anyString());
        assertThat(lifecycle.current(command.id()).status()).isEqualTo(CommandStatus.WAITING_FOR_QA);
    }

    @Test
    void negativeQaStageIsSentWithoutApproval() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.ACCEPTED, 2, null, false);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(gateway.getBatch(anyString())).thenReturn(new BatchState(true, 2, false));

        worker.runOnce();

        verify(sender).send(
                org.mockito.ArgumentMatchers.argThat(value ->
                        value.status() == CommandStatus.READY_TO_SUBMIT && !value.qaPassed()),
                eq("0xsig"), eq(OWNER), eq(NOW));
    }

    @Test
    void retryableStorageFailureSetsRetryAtAndKeepsPinning() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.ACCEPTED, 0, null, true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(storage.store(any())).thenThrow(new ReportStorageException(
                ReportStorageException.Reason.UNAVAILABLE, "down", null));

        worker.runOnce();

        StageCommand current = lifecycle.current(command.id());
        assertThat(current.status()).isEqualTo(CommandStatus.PINNING);
        assertThat(current.retryAt()).isEqualTo(NOW.plusSeconds(5));
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void permanentStorageFailureIsExplicitFailed() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.ACCEPTED, 0, null, true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(storage.store(any())).thenThrow(new ReportStorageException(
                ReportStorageException.Reason.REJECTED, "rejected", null));

        worker.runOnce();

        StageCommand current = lifecycle.current(command.id());
        assertThat(current.status()).isEqualTo(CommandStatus.FAILED);
        assertThat(current.failureCode()).isEqualTo("REPORT_STORAGE_REJECTED");
        assertThat(current.retryAllowedAfter()).isEqualTo(1800000600L);
    }

    @Test
    void preflightStageMismatchIsExplicitFailed() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.READY_TO_SUBMIT, 0, "bafy", true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(gateway.getBatch(anyString())).thenReturn(new BatchState(true, 1, false));

        worker.runOnce();

        StageCommand current = lifecycle.current(command.id());
        assertThat(current.status()).isEqualTo(CommandStatus.FAILED);
        assertThat(current.failureCode()).isEqualTo("UNEXPECTED_STAGE");
        assertThat(current.retryAllowedAfter()).isEqualTo(1800000600L);
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void rpcUnavailabilityDuringPreflightSetsRetryAt() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.READY_TO_SUBMIT, 0, "bafy", true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(gateway.getBatch(anyString())).thenThrow(new ChainUnavailableException("down", null));

        worker.runOnce();

        StageCommand current = lifecycle.current(command.id());
        assertThat(current.status()).isEqualTo(CommandStatus.READY_TO_SUBMIT);
        assertThat(current.retryAt()).isEqualTo(NOW.plusSeconds(5));
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void revertedReceiptFinishesAsFailed() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.SUBMITTED, 0, "bafy", true);
        lifecycle.put(command);
        TransactionAttempt attempt = attempt(command.id());
        when(recovery.claimNext(anyString(), any(), any())).thenReturn(
                Optional.of(new SubmittedAttempt(command, attempt)));
        when(gateway.findReceipt(attempt.txHash())).thenReturn(Optional.of(
                new ChainReceipt(TransactionReceiptStatus.REVERTED, 9L, "0x" + "9".repeat(64))));

        worker.runOnce();

        assertThat(lifecycle.current(command.id()).status()).isEqualTo(CommandStatus.FAILED);
        assertThat(lifecycle.current(command.id()).failureCode()).isEqualTo("RECEIPT_REVERTED");
        verify(attempts).update(any());
    }

    @Test
    void successfulReceiptLeavesTheStatusForTheIndexer() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.SUBMITTED, 0, "bafy", true);
        lifecycle.put(command);
        TransactionAttempt attempt = attempt(command.id());
        when(recovery.claimNext(anyString(), any(), any())).thenReturn(
                Optional.of(new SubmittedAttempt(command, attempt)));
        when(gateway.findReceipt(attempt.txHash())).thenReturn(Optional.of(
                new ChainReceipt(TransactionReceiptStatus.SUCCESS, 9L, "0x" + "9".repeat(64))));

        worker.runOnce();

        assertThat(lifecycle.current(command.id()).status()).isEqualTo(CommandStatus.SUBMITTED);
        verify(attempts).update(any());
        verify(gateway, never()).sendRawTransaction(anyString());
    }

    @Test
    void missingReceiptResendsTheStoredBytesAndKeepsSubmitted() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.SUBMITTED, 0, "bafy", true);
        lifecycle.put(command);
        TransactionAttempt attempt = attempt(command.id());
        when(recovery.claimNext(anyString(), any(), any())).thenReturn(
                Optional.of(new SubmittedAttempt(command, attempt)));
        when(gateway.findReceipt(attempt.txHash())).thenReturn(Optional.empty());
        when(gateway.sendRawTransaction(attempt.signedTx())).thenReturn(attempt.txHash());

        worker.runOnce();

        assertThat(lifecycle.current(command.id()).status()).isEqualTo(CommandStatus.SUBMITTED);
        verify(gateway).sendRawTransaction(attempt.signedTx());
        verify(attempts).update(any());
    }

    @Test
    void noTransitionEverUsesConfirmed() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.ACCEPTED, 2, null, true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));

        worker.runOnce();

        assertThat(lifecycle.targets()).noneMatch(status -> status == CommandStatus.CONFIRMED);
    }

    @Test
    void rejectedStationCertificateIsExplicitFailed() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.READY_TO_SUBMIT, 0, "bafy", true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(gateway.getBatch(anyString())).thenReturn(new BatchState(true, 0, false));
        when(verifier.verifyForReport(anyString(), anyString(), any(Integer.class))).thenReturn(
                new StationIdentityVerification(
                        StationIdentityVerification.Reason.REVOKED, "revoked", null, null, null));

        worker.runOnce();

        StageCommand current = lifecycle.current(command.id());
        assertThat(current.status()).isEqualTo(CommandStatus.FAILED);
        assertThat(current.failureCode()).isEqualTo("STATION_NOT_ACTIVE");
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void stationRegistryUnavailabilityDuringPreflightSetsRetryAt() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.READY_TO_SUBMIT, 0, "bafy", true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(gateway.getBatch(anyString())).thenReturn(new BatchState(true, 0, false));
        when(verifier.verifyForReport(anyString(), anyString(), any(Integer.class))).thenThrow(
                new pl.pz.supplyblockchain.application.ports.StationRegistryException(
                        pl.pz.supplyblockchain.application.ports.StationRegistryException.Reason.UNAVAILABLE,
                        "rpc down", null));

        worker.runOnce();

        StageCommand current = lifecycle.current(command.id());
        assertThat(current.status()).isEqualTo(CommandStatus.READY_TO_SUBMIT);
        assertThat(current.retryAt()).isEqualTo(NOW.plusSeconds(5));
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void deadlineAlreadyPassedOnChainIsExplicitFailed() {
        StageCommand command = command(CommandType.STAGE, CommandStatus.READY_TO_SUBMIT, 0, "bafy", true);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(payloads.findById(command.id())).thenReturn(Optional.of(payload(command)));
        when(gateway.getBatch(anyString())).thenReturn(new BatchState(true, 0, false));
        when(confirmedTime.confirmedTimestamp()).thenReturn(1800000601L);

        worker.runOnce();

        StageCommand current = lifecycle.current(command.id());
        assertThat(current.status()).isEqualTo(CommandStatus.FAILED);
        assertThat(current.failureCode()).isEqualTo("DEADLINE_EXPIRED");
        assertThat(current.retryAllowedAfter()).isEqualTo(1800000600L);
    }

    @Test
    void createBatchPreflightRejectsAnAlreadyExistingBatch() {
        StageCommand command = command(CommandType.CREATE_BATCH, CommandStatus.ACCEPTED, 0, null, null);
        lifecycle.put(command);
        when(leases.acquireNext(anyString(), any(), any())).thenReturn(Optional.of(command));
        when(gateway.getBatch(anyString())).thenReturn(new BatchState(true, 0, false));

        worker.runOnce();

        StageCommand current = lifecycle.current(command.id());
        assertThat(current.status()).isEqualTo(CommandStatus.FAILED);
        assertThat(current.failureCode()).isEqualTo("BATCH_ALREADY_EXISTS");
        verify(sender, never()).send(any(), any(), any(), any());
    }

    private WorkerCommandPayload payload(StageCommand command) {
        return new WorkerCommandPayload(command, JCS, "0xsig");
    }

    private StageCommand command(
            CommandType type, CommandStatus status, int stage, String cid, Boolean qaPassed) {
        return new StageCommand(
                UUID.randomUUID(), type, STATION, "0x" + "a".repeat(64),
                type == CommandType.CREATE_BATCH ? "B-2026-0001" : null,
                type == CommandType.CREATE_BATCH ? null : stage,
                type == CommandType.CREATE_BATCH ? null : STATION,
                type == CommandType.CREATE_BATCH ? null : CERTIFICATE,
                type == CommandType.CREATE_BATCH ? null : "0x" + "b".repeat(64),
                type == CommandType.CREATE_BATCH ? null : "0x" + "c".repeat(64),
                type == CommandType.CREATE_BATCH ? null : 1800000600L,
                type == CommandType.CREATE_BATCH ? null : qaPassed,
                status, null, cid, null, null, null,
                NOW.minusSeconds(10), NOW.minusSeconds(10));
    }

    private TransactionAttempt attempt(UUID commandId) {
        return new TransactionAttempt(
                UUID.randomUUID(), commandId, STATION,
                java.math.BigDecimal.ONE, "0x" + "d".repeat(64), "0xraw",
                "0x" + "e".repeat(64), java.math.BigDecimal.ONE, java.math.BigDecimal.TEN,
                java.math.BigDecimal.valueOf(21000), NOW, null, null, null, NOW);
    }

    private static final class FakeLifecycle implements CommandLifecycleRepository {
        private final Map<UUID, StageCommand> commands = new HashMap<>();
        private final java.util.List<CommandStatus> targets = new java.util.ArrayList<>();

        void put(StageCommand command) {
            commands.put(command.id(), command);
        }

        StageCommand current(UUID id) {
            return commands.get(id);
        }

        java.util.List<CommandStatus> targets() {
            return targets;
        }

        @Override
        public StageCommand transition(
                UUID commandId, String leaseOwner, CommandStatus expectedStatus,
                CommandStatus nextStatus, String cid, String failureCode,
                Long retryAllowedAfter, Instant retryAt, Instant now) {
            StageCommand current = commands.get(commandId);
            if (current == null || !OWNER.equals(leaseOwner) || current.status() != expectedStatus) {
                throw new LeaseLostException("lease lost");
            }
            targets.add(nextStatus);
            StageCommand updated = new StageCommand(
                    current.id(), current.type(), current.ownerAddress(), current.batchId(),
                    current.batchNumber(), current.stageId(), current.stationAddress(),
                    current.stationCertificateId(), current.reportHash(), current.signedPayloadDigest(),
                    current.deadline(), current.qaPassed(), nextStatus, current.outcome(),
                    cid == null ? current.cid() : cid,
                    nextStatus == CommandStatus.FAILED ? failureCode : null,
                    retryAt,
                    nextStatus == CommandStatus.FAILED ? retryAllowedAfter : null,
                    current.createdAt(), now);
            commands.put(commandId, updated);
            return updated;
        }
    }
}
