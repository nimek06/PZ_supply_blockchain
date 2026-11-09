package pl.pz.supplyblockchain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.application.commands.SubmitStageCommand;
import pl.pz.supplyblockchain.application.ports.CanonicalReport;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
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
import pl.pz.supplyblockchain.domain.TelemetryReport;

class SubmitStageServiceTest {

    private final CommandRepository commandRepository = mock(CommandRepository.class);
    private final ReportCanonicalizer reportCanonicalizer = mock(ReportCanonicalizer.class);
    private final StageCompletionVerifier signatureVerifier = mock(StageCompletionVerifier.class);
    private final ProfileValidator profileValidator = mock(ProfileValidator.class);
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
                commandRepository, reportCanonicalizer, signatureVerifier, profileValidator, chainProperties, clock);
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
}
