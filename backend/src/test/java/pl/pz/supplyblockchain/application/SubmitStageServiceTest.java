package pl.pz.supplyblockchain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.application.commands.SubmitStageCommand;
import pl.pz.supplyblockchain.application.ports.ChainUnavailableException;
import pl.pz.supplyblockchain.application.ports.CanonicalReport;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.application.ports.ConfirmedBlockTimeSource;
import pl.pz.supplyblockchain.application.ports.NewStageCommand;
import pl.pz.supplyblockchain.application.ports.ReportCanonicalizer;
import pl.pz.supplyblockchain.application.ports.StageCompletionVerifier;
import pl.pz.supplyblockchain.application.ports.StationCertificateRepository;
import pl.pz.supplyblockchain.application.ports.StationCertificateVerifier;
import pl.pz.supplyblockchain.application.ports.StationRegistryException;
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
import pl.pz.supplyblockchain.domain.TelemetryReport;

class SubmitStageServiceTest {

    private final CommandRepository commandRepository = mock(CommandRepository.class);
    private final ReportCanonicalizer reportCanonicalizer = mock(ReportCanonicalizer.class);
    private final StageCompletionVerifier signatureVerifier = mock(StageCompletionVerifier.class);
    private final ProfileValidator profileValidator = mock(ProfileValidator.class);
    private final StationCertificateRepository certificateRepository = mock(StationCertificateRepository.class);
    private final StationCertificateVerifier certificateVerifier = mock(StationCertificateVerifier.class);
    private final TransactionAttemptRepository attemptRepository = mock(TransactionAttemptRepository.class);
    private final ConfirmedBlockTimeSource confirmedBlockTime = mock(ConfirmedBlockTimeSource.class);
    private final ChainProperties chainProperties = new ChainProperties(
            31337, "0x5fbdb2315678afecb367f032d93f642f64180aa3", "SupplyChain", "1", 3600);
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1800000000L), ZoneOffset.UTC);

    private SubmitStageService service;

    private final String batchId = "0x79709e7401c7c94e8c6439dd2cd3729509c3c33d6b80b2df940e9c435df582f1";
    private final String stationAddress = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private final String reportHash = "0x388322c4e5f9429a457ab80640548381b4cb000d14e933561f40db7f2b017ffe";
    private final String digest = "0xa1b2c3d4e5f60000000000000000000000000000000000000000000000000000";
    private final String certificateId = "0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e";

    @BeforeEach
    void setUp() {
        service = new SubmitStageService(
                commandRepository, reportCanonicalizer, signatureVerifier, profileValidator,
                certificateRepository, certificateVerifier, attemptRepository, confirmedBlockTime,
                chainProperties, clock);
        when(certificateRepository.findById(certificateId)).thenReturn(
                Optional.of(new pl.pz.supplyblockchain.domain.StationCertificate(
                        certificateId, stationAddress, "{}", Instant.ofEpochSecond(1800000000L))));
        when(certificateVerifier.verifyForReport(any(), any(), any(Integer.class))).thenReturn(
                new pl.pz.supplyblockchain.domain.StationIdentityVerification(
                        pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.OK,
                        "ok", null, null, null));
    }

    private TelemetryReport sampleReport() {
        return new TelemetryReport(
                1, batchId, 2, stationAddress, 1800000000L, Map.of("temp", 21500L), true);
    }

    private SubmitStageCommand sampleCommand(long deadline) {
        return new SubmitStageCommand(
                batchId, sampleReport(), certificateId, reportHash, deadline, "0xsig", Optional.empty());
    }

    @Test
    void submitRejectsCertificateIdThatIsNotBytes32() {
        SubmitStageCommand command = new SubmitStageCommand(
                batchId, sampleReport(), "0xcert", reportHash, 1800000600L, "0xsig", Optional.empty());

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.SCHEMA_VIOLATION));
    }

    @Test
    void submitSuccess() {
        long deadline = 1800000600L;
        SubmitStageCommand command = sampleCommand(deadline);

        when(reportCanonicalizer.canonicalize(any())).thenReturn(
                new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(
                new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(commandRepository.findLatestStageCommand(any(), any(), any(Integer.class))).thenReturn(Optional.empty());
        when(commandRepository.saveNew(any())).thenAnswer(invocation -> {
            NewStageCommand cmd = invocation.getArgument(0);
            return cmd.command();
        });

        StageCommand result = service.submit(command);

        assertThat(result.batchId()).isEqualTo(batchId);
        assertThat(result.status()).isEqualTo(CommandStatus.ACCEPTED);
        assertThat(result.ownerAddress()).isEqualTo(stationAddress);
        assertThat(result.signedPayloadDigest()).isEqualTo(digest);
        verify(commandRepository).saveNew(any());
    }

    @Test
    void submitExpiredDeadline() {
        SubmitStageCommand command = sampleCommand(1799999999L);
        when(reportCanonicalizer.canonicalize(any())).thenReturn(
                new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(
                new SignatureVerification(stationAddress, digest));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.DEADLINE_EXPIRED));
    }

    @Test
    void submitDeadlineTooFar() {
        SubmitStageCommand command = sampleCommand(1800003601L);
        when(reportCanonicalizer.canonicalize(any())).thenReturn(
                new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(
                new SignatureVerification(stationAddress, digest));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.DEADLINE_TOO_FAR));
    }

    @Test
    void submitHashMismatch() {
        SubmitStageCommand command = sampleCommand(1800000600L);

        when(reportCanonicalizer.canonicalize(any())).thenReturn(
                new CanonicalReport("{}".getBytes(), "0x0000000000000000000000000000000000000000000000000000000000000000"));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.SCHEMA_VIOLATION));
    }

    @Test
    void submitSignerMismatch() {
        SubmitStageCommand command = sampleCommand(1800000600L);

        when(reportCanonicalizer.canonicalize(any())).thenReturn(
                new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(
                new SignatureVerification("0x3c44cdd05a9000f507687041266870c569253590", digest));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.SIGNER_MISMATCH));
    }

    @Test
    void submitIdempotentSameDigest() {
        long deadline = 1800000600L;
        SubmitStageCommand command = sampleCommand(deadline);
        StageCommand existing = new StageCommand(
                UUID.randomUUID(), CommandType.STAGE, stationAddress, batchId, null, 2,
                stationAddress, "0xcert", reportHash, digest, deadline, true,
                CommandStatus.ACCEPTED, null, null, null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.of(existing));

        StageCommand result = service.submit(command);

        assertThat(result.id()).isEqualTo(existing.id());
    }

    @Test
    void submitRejectsReplayWhenStationIsNoLongerActive() {
        record Case(pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason reason,
                    ErrorCode code) {
        }
        List<Case> cases = List.of(
                new Case(pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.REVOKED,
                        ErrorCode.STATION_NOT_ACTIVE),
                new Case(pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.EXPIRED,
                        ErrorCode.CERTIFICATE_INVALID));

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));

        for (Case testCase : cases) {
            when(certificateVerifier.verifyForReport(any(), any(), any(Integer.class))).thenReturn(
                    new pl.pz.supplyblockchain.domain.StationIdentityVerification(
                            testCase.reason(), "rejected", null, null, null));

            assertThatThrownBy(() -> service.submit(sampleCommand(1800000600L)))
                    .isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(testCase.code()));
        }

        verify(commandRepository, never()).findBySignedPayloadDigest(any(), any());
    }

    @Test
    void submitRejectsReplayWhenStationRegistryIsUnavailable() {
        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(certificateVerifier.verifyForReport(any(), any(), any(Integer.class))).thenThrow(
                new StationRegistryException(StationRegistryException.Reason.UNAVAILABLE, "rpc down", null));

        assertThatThrownBy(() -> service.submit(sampleCommand(1800000600L)))
                .isInstanceOf(StationRegistryException.class);

        verify(commandRepository, never()).findBySignedPayloadDigest(any(), any());
    }

    @Test
    void submitRejectsConfirmedCommandWithoutOutcome() {
        long deadline = 1800000600L;
        StageCommand confirmed = new StageCommand(
                UUID.randomUUID(), CommandType.STAGE, stationAddress, batchId, null, 2,
                stationAddress, "0xcert", reportHash, "0xotherdigest", deadline, true,
                CommandStatus.CONFIRMED, null, "bafy", null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(commandRepository.findLatestStageCommand(any(), any(), any(Integer.class)))
                .thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.submit(sampleCommand(deadline)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code())
                        .isEqualTo(ErrorCode.STAGE_ALREADY_COMPLETED));

        verify(commandRepository, never()).saveNew(any());
    }

    @Test
    void submitRejectsActiveStageCommand() {
        long deadline = 1800000600L;
        SubmitStageCommand command = sampleCommand(deadline);
        StageCommand active = new StageCommand(
                UUID.randomUUID(), CommandType.STAGE, stationAddress, batchId, null, 2,
                stationAddress, "0xcert", reportHash, "0xotherdigest", deadline, true,
                CommandStatus.ACCEPTED, null, null, null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(commandRepository.findLatestStageCommand(any(), any(), any(Integer.class))).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.STAGE_ALREADY_PENDING));
    }

    @Test
    void submitRejectsCompletedConfirmedAcceptedStage() {
        long deadline = 1800000600L;
        SubmitStageCommand command = sampleCommand(deadline);
        StageCommand confirmed = new StageCommand(
                UUID.randomUUID(), CommandType.STAGE, stationAddress, batchId, null, 2,
                stationAddress, "0xcert", reportHash, "0xotherdigest", deadline, true,
                CommandStatus.CONFIRMED, CommandOutcome.ACCEPTED, "bafy", null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(commandRepository.findLatestStageCommand(any(), any(), any(Integer.class))).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.STAGE_ALREADY_COMPLETED));
    }

    @Test
    void submitAllowsReplacementAfterConfirmedRejected() {
        long deadline = 1800000600L;
        SubmitStageCommand command = sampleCommand(deadline);
        StageCommand rejected = new StageCommand(
                UUID.randomUUID(), CommandType.STAGE, stationAddress, batchId, null, 2,
                stationAddress, "0xcert", reportHash, "0xotherdigest", deadline, false,
                CommandStatus.CONFIRMED, CommandOutcome.REJECTED, "bafy", null, null, null,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(commandRepository.findLatestStageCommand(any(), any(), any(Integer.class))).thenReturn(Optional.of(rejected));
        when(commandRepository.saveNew(any())).thenAnswer(invocation -> ((NewStageCommand) invocation.getArgument(0)).command());

        StageCommand result = service.submit(command);

        assertThat(result.status()).isEqualTo(CommandStatus.ACCEPTED);
    }

    @Test
    void submitRejectsMissingStationCertificate() {
        long deadline = 1800000600L;
        SubmitStageCommand command = sampleCommand(deadline);

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.CERTIFICATE_INVALID));
    }

    @Test
    void submitRejectsInactiveStationCertificate() {
        long deadline = 1800000600L;
        SubmitStageCommand command = sampleCommand(deadline);

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(certificateVerifier.verifyForReport(any(), any(), any(Integer.class))).thenReturn(
                new pl.pz.supplyblockchain.domain.StationIdentityVerification(
                        pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.NOT_REGISTERED,
                        "station not registered", null, null, null));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.STATION_NOT_ACTIVE));
    }

    @Test
    void submitRejectsExpiredStationCertificate() {
        long deadline = 1800000600L;
        SubmitStageCommand command = sampleCommand(deadline);

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(certificateVerifier.verifyForReport(any(), any(), any(Integer.class))).thenReturn(
                new pl.pz.supplyblockchain.domain.StationIdentityVerification(
                        pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.EXPIRED,
                        "certificate expired", null, null, null));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.CERTIFICATE_INVALID));
    }

    @Test
    void submitRejectsStageNotAllowedByCertificate() {
        long deadline = 1800000600L;
        SubmitStageCommand command = sampleCommand(deadline);

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(certificateVerifier.verifyForReport(any(), any(), any(Integer.class))).thenReturn(
                new pl.pz.supplyblockchain.domain.StationIdentityVerification(
                        pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.STAGE_NOT_ALLOWED,
                        "stage not allowed", null, null, null));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.STAGE_NOT_ALLOWED));
    }

    @Test
    void submitRejectsTokenThatDoesNotBelongToReportSigner() {
        SubmitStageCommand command = new SubmitStageCommand(
                batchId, sampleReport(), certificateId, reportHash, 1800000600L, "0xsig",
                Optional.of("0x3c44cdd05a9000f507687041266870c569253590"));

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(
                new SignatureVerification(stationAddress, digest));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.SIGNER_MISMATCH));
    }

    @Test
    void submitRejectsInvalidCertificateReasons() {
        record Case(pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason reason,
                    ErrorCode code) {
        }
        var cases = java.util.List.of(
                new Case(pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.UNTRUSTED_ISSUER,
                        ErrorCode.CERTIFICATE_INVALID),
                new Case(pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.WRONG_DEPLOYMENT,
                        ErrorCode.CERTIFICATE_INVALID),
                new Case(pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.NOT_YET_VALID,
                        ErrorCode.CERTIFICATE_INVALID),
                new Case(pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.REVOKED,
                        ErrorCode.STATION_NOT_ACTIVE),
                new Case(pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason.REGISTRY_MISMATCH,
                        ErrorCode.REGISTRY_MISMATCH));

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(
                new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());

        for (Case testCase : cases) {
            when(certificateVerifier.verifyForReport(any(), any(), any(Integer.class))).thenReturn(
                    new pl.pz.supplyblockchain.domain.StationIdentityVerification(
                            testCase.reason(), "rejected", null, null, null));

            assertThatThrownBy(() -> service.submit(sampleCommand(1800000600L)))
                    .isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(testCase.code()));
        }
    }

    @Test
    void submitPropagatesStationRegistryUnavailability() {
        SubmitStageCommand command = sampleCommand(1800000600L);

        when(reportCanonicalizer.canonicalize(any())).thenReturn(new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(
                new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(certificateVerifier.verifyForReport(any(), any(), any(Integer.class))).thenThrow(
                new StationRegistryException(StationRegistryException.Reason.UNAVAILABLE, "rpc down", null));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(StationRegistryException.class);
    }

    @Test
    void submitRejectsFailedCommandWithUnresolvedAttempts() {
        StageCommand failed = failedCommand(1800000600L, null);
        stubReplacement(failed);
        when(attemptRepository.hasUnresolvedAttempts(failed.id())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(sampleCommand(1800000600L)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException domain = (DomainException) e;
                    assertThat(domain.code()).isEqualTo(ErrorCode.STAGE_ALREADY_PENDING);
                    assertThat(domain.retryAllowedAfter()).isEqualTo(1800000600L);
                });
    }

    @Test
    void submitRejectsFailedCommandBeforeConfirmedDeadline() {
        StageCommand failed = failedCommand(1800000600L, null);
        stubReplacement(failed);
        when(attemptRepository.hasUnresolvedAttempts(failed.id())).thenReturn(false);
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(1800000600L);

        assertThatThrownBy(() -> service.submit(sampleCommand(1800000600L)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException domain = (DomainException) e;
                    assertThat(domain.code()).isEqualTo(ErrorCode.STAGE_ALREADY_PENDING);
                    assertThat(domain.retryAllowedAfter()).isEqualTo(1800000600L);
                });
    }

    @Test
    void submitAllowsFailedReplacementAfterConfirmedDeadline() {
        StageCommand failed = failedCommand(1800000600L, null);
        stubReplacement(failed);
        when(attemptRepository.hasUnresolvedAttempts(failed.id())).thenReturn(false);
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(1800000601L);
        when(commandRepository.saveNew(any())).thenAnswer(
                invocation -> ((NewStageCommand) invocation.getArgument(0)).command());

        StageCommand result = service.submit(sampleCommand(1800000600L));

        assertThat(result.status()).isEqualTo(CommandStatus.ACCEPTED);
    }

    @Test
    void submitAllowsReplacementAfterDefiniteSendRejection() {
        StageCommand failed = failedCommand(1800000600L, null, true, "SEND_REJECTED");
        stubReplacement(failed);
        when(attemptRepository.hasUnresolvedAttempts(failed.id())).thenReturn(true);
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(1800000601L);
        when(commandRepository.saveNew(any())).thenAnswer(
                invocation -> ((NewStageCommand) invocation.getArgument(0)).command());

        StageCommand result = service.submit(sampleCommand(1800000600L));

        assertThat(result.status()).isEqualTo(CommandStatus.ACCEPTED);
    }

    @Test
    void submitRejectsUnresolvedAttemptsUnlessSendWasDefinitelyRejected() {
        StageCommand failed = failedCommand(1800000600L, null, true, "DEADLINE_EXPIRED");
        stubReplacement(failed);
        when(attemptRepository.hasUnresolvedAttempts(failed.id())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(sampleCommand(1800000600L)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code())
                        .isEqualTo(ErrorCode.STAGE_ALREADY_PENDING));
    }

    @Test
    void submitUsesRetryAllowedAfterAsHigherFailedThreshold() {
        StageCommand failed = failedCommand(1800000600L, 1800001200L);
        stubReplacement(failed);
        when(attemptRepository.hasUnresolvedAttempts(failed.id())).thenReturn(false);
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(1800000900L);

        assertThatThrownBy(() -> service.submit(sampleCommand(1800000600L)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).retryAllowedAfter()).isEqualTo(1800001200L));
    }

    @Test
    void submitAllowsFailedReplacementAfterRetryAllowedAfter() {
        StageCommand failed = failedCommand(1800000600L, 1800001200L);
        stubReplacement(failed);
        when(attemptRepository.hasUnresolvedAttempts(failed.id())).thenReturn(false);
        when(confirmedBlockTime.confirmedTimestamp()).thenReturn(1800001201L);
        when(commandRepository.saveNew(any())).thenAnswer(
                invocation -> ((NewStageCommand) invocation.getArgument(0)).command());

        StageCommand result = service.submit(sampleCommand(1800000600L));

        assertThat(result.status()).isEqualTo(CommandStatus.ACCEPTED);
    }

    @Test
    void submitAllowsImmediateReplacementOfFailedQaRejectedCommand() {
        StageCommand failed = failedCommand(1800000600L, null, false);
        stubReplacement(failed);
        when(attemptRepository.hasUnresolvedAttempts(failed.id())).thenReturn(false);
        when(commandRepository.saveNew(any())).thenAnswer(
                invocation -> ((NewStageCommand) invocation.getArgument(0)).command());

        StageCommand result = service.submit(sampleCommand(1800000600L));

        assertThat(result.status()).isEqualTo(CommandStatus.ACCEPTED);
        org.mockito.Mockito.verifyNoInteractions(confirmedBlockTime);
    }

    @Test
    void submitFailsClosedWhenConfirmedTimeIsUnavailable() {
        StageCommand failed = failedCommand(1800000600L, null);
        stubReplacement(failed);
        when(attemptRepository.hasUnresolvedAttempts(failed.id())).thenReturn(false);
        when(confirmedBlockTime.confirmedTimestamp()).thenThrow(
                new ChainUnavailableException("rpc down", null));

        assertThatThrownBy(() -> service.submit(sampleCommand(1800000600L)))
                .isInstanceOf(ChainUnavailableException.class);
    }

    private void stubReplacement(StageCommand latest) {
        when(reportCanonicalizer.canonicalize(any())).thenReturn(
                new CanonicalReport("{}".getBytes(), reportHash));
        when(signatureVerifier.verifyCompletion(any(), any())).thenReturn(
                new SignatureVerification(stationAddress, digest));
        when(commandRepository.findBySignedPayloadDigest(any(), any())).thenReturn(Optional.empty());
        when(commandRepository.findLatestStageCommand(any(), any(), any(Integer.class)))
                .thenReturn(Optional.of(latest));
    }

    private StageCommand failedCommand(long deadline, Long retryAllowedAfter) {
        return failedCommand(deadline, retryAllowedAfter, true);
    }

    private StageCommand failedCommand(long deadline, Long retryAllowedAfter, boolean qaPassed) {
        return failedCommand(deadline, retryAllowedAfter, qaPassed, "DEADLINE_EXPIRED");
    }

    private StageCommand failedCommand(
            long deadline, Long retryAllowedAfter, boolean qaPassed, String failureCode) {
        return new StageCommand(
                UUID.randomUUID(), CommandType.STAGE, stationAddress, batchId, null, 2,
                stationAddress, certificateId, reportHash, "0xotherdigest", deadline, qaPassed,
                CommandStatus.FAILED, null, null, failureCode, null, retryAllowedAfter,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));
    }
}
