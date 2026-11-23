package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.web3j.crypto.Credentials;
import pl.pz.supplyblockchain.application.ports.ChainUnavailableException;
import pl.pz.supplyblockchain.application.ports.CommandSubmissionRepository;
import pl.pz.supplyblockchain.application.ports.RelayerChainGateway;
import pl.pz.supplyblockchain.application.ports.RelayerTransactionException;
import pl.pz.supplyblockchain.application.ports.TransactionAttemptRepository;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.config.RelayerProperties;
import pl.pz.supplyblockchain.config.RelayerWorkerProperties;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.StageCommand;
import pl.pz.supplyblockchain.domain.TransactionAttempt;

class RelayerTransactionSenderTest {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String SIGNATURE = "0x" + "1".repeat(130);
    private static final String PRIVATE_KEY =
            "0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final RelayerChainGateway gateway = mock(RelayerChainGateway.class);
    private final CommandSubmissionRepository submissions = mock(CommandSubmissionRepository.class);
    private final TransactionAttemptRepository attempts = mock(TransactionAttemptRepository.class);
    private final Credentials credentials = Credentials.create(PRIVATE_KEY);
    private final List<TransactionAttempt> persisted = new ArrayList<>();

    private RelayerTransactionSender sender;

    @BeforeEach
    void setUp() {
        sender = new RelayerTransactionSender(
                gateway,
                new Eip1559TransactionSigner(),
                new CreateBatchCallEncoder(),
                new CompleteStageCallEncoder(),
                submissions,
                attempts,
                new ChainProperties(31337, CONTRACT, "SupplyChain", "1", 3600),
                new RelayerProperties(30),
                new RelayerWorkerProperties(true, 2000, 30, 5, 120, "worker-1"),
                credentials);
        when(gateway.pendingNonce(anyString())).thenReturn(BigInteger.valueOf(5));
        when(gateway.maxPriorityFeePerGas()).thenReturn(BigInteger.ONE);
        when(gateway.latestBaseFeePerGas()).thenReturn(BigInteger.TEN);
        when(gateway.estimateGas(anyString(), anyString(), anyString())).thenReturn(BigInteger.valueOf(100_000));
        doAnswer(invocation -> {
            TransactionAttempt attempt = invocation.getArgument(0);
            persisted.add(attempt);
            return attempt;
        }).when(submissions).reserveAndMarkSubmitted(any(), anyString(), any());
        when(attempts.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void persistsBeforeBroadcastAndReturnsBroadcastTimestamp() {
        when(gateway.sendRawTransaction(anyString()))
                .thenAnswer(invocation -> persisted.getFirst().txHash());

        TransactionAttempt result = sender.send(createCommand(), null, "worker-1", NOW);

        InOrder order = inOrder(submissions, gateway);
        order.verify(submissions).reserveAndMarkSubmitted(any(), anyString(), any());
        order.verify(gateway).sendRawTransaction(anyString());

        assertThat(persisted).hasSize(1);
        assertThat(result.broadcastAt()).isEqualTo(NOW);
        assertThat(result.txNonce()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(result.maxPriorityFeePerGas()).isEqualByComparingTo(BigDecimal.valueOf(30_000_000_000L));
        assertThat(result.maxFeePerGas()).isEqualByComparingTo(BigDecimal.valueOf(30_000_000_020L));
        assertThat(result.gasLimit()).isEqualByComparingTo(BigDecimal.valueOf(120_000));
        assertThat(result.signedTx()).startsWith("0x");
        assertThat(result.txHash()).startsWith("0x");
    }

    @Test
    void twoCommandsUseConsecutivePendingNonces() {
        when(gateway.pendingNonce(anyString())).thenReturn(BigInteger.valueOf(5), BigInteger.valueOf(6));
        when(gateway.sendRawTransaction(anyString())).thenAnswer(invocation -> {
            // The sender persists the attempt first; return the hash of the latest one.
            return persisted.getLast().txHash();
        });

        sender.send(createCommand(), null, "worker-1", NOW);
        sender.send(createCommand(), null, "worker-1", NOW);

        assertThat(persisted).hasSize(2);
        assertThat(persisted.get(0).txNonce()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(persisted.get(1).txNonce()).isEqualByComparingTo(BigDecimal.valueOf(6));
    }

    @Test
    void timeoutLeavesThePersistedAttemptForRecovery() {
        when(gateway.sendRawTransaction(anyString()))
                .thenThrow(new ChainUnavailableException("timeout", null));

        assertThatThrownBy(() -> sender.send(createCommand(), null, "worker-1", NOW))
                .isInstanceOf(ChainUnavailableException.class);

        assertThat(persisted).hasSize(1);
        assertThat(persisted.getFirst().txHash()).isNotBlank();
        assertThat(persisted.getFirst().signedTx()).isNotBlank();
    }

    @Test
    void differentReturnedHashIsNeverTreatedAsASecondOperation() {
        when(gateway.sendRawTransaction(anyString())).thenReturn("0x" + "f".repeat(64));

        assertThatThrownBy(() -> sender.send(createCommand(), null, "worker-1", NOW))
                .isInstanceOf(RelayerTransactionException.class)
                .satisfies(exception -> assertThat(
                        ((RelayerTransactionException) exception).retryable()).isTrue());

        assertThat(persisted).hasSize(1);
    }

    @Test
    void stageUsesStoredCidSignatureAndEmptyApproval() {
        when(gateway.sendRawTransaction(anyString()))
                .thenAnswer(invocation -> persisted.getFirst().txHash());

        sender.send(stageCommand(), SIGNATURE, "worker-1", NOW);

        assertThat(persisted).hasSize(1);
        assertThat(persisted.getFirst().signedTx()).isNotBlank();
    }

    private StageCommand createCommand() {
        return new StageCommand(
                UUID.randomUUID(), CommandType.CREATE_BATCH, STATION, "0x" + "a".repeat(64),
                "B-2026-0001", null, null, null, null, null, null, null,
                CommandStatus.READY_TO_SUBMIT, null, null, null, null, null,
                NOW.minusSeconds(5), NOW.minusSeconds(5));
    }

    private StageCommand stageCommand() {
        return new StageCommand(
                UUID.randomUUID(), CommandType.STAGE, STATION, "0x" + "a".repeat(64),
                null, 0, STATION, "0xcert", "0x" + "b".repeat(64), "0x" + "c".repeat(64),
                1800000600L, true, CommandStatus.READY_TO_SUBMIT, null,
                "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi",
                null, null, null, NOW.minusSeconds(5), NOW.minusSeconds(5));
    }
}
