package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import pl.pz.supplyblockchain.application.ports.CommandSubmissionRepository;
import pl.pz.supplyblockchain.application.ports.CompleteStageCall;
import pl.pz.supplyblockchain.application.ports.Eip1559Transaction;
import pl.pz.supplyblockchain.application.ports.RelayerChainGateway;
import pl.pz.supplyblockchain.application.ports.RelayerTransactionException;
import pl.pz.supplyblockchain.application.ports.SignedTransaction;
import pl.pz.supplyblockchain.application.ports.TransactionAttemptRepository;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.config.RelayerProperties;
import pl.pz.supplyblockchain.config.RelayerWorkerProperties;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.StageCommand;
import pl.pz.supplyblockchain.domain.TransactionAttempt;

/**
 * One writer for the relayer account. It signs and persists the attempt before broadcast; an uncertain
 * RPC result never creates a second operation.
 */
public class RelayerTransactionSender {

    private final RelayerChainGateway gateway;
    private final Eip1559TransactionSigner signer;
    private final CreateBatchCallEncoder createBatchEncoder;
    private final CompleteStageCallEncoder completeStageEncoder;
    private final CommandSubmissionRepository submissionRepository;
    private final TransactionAttemptRepository attemptRepository;
    private final ChainProperties chainProperties;
    private final RelayerProperties relayerProperties;
    private final RelayerWorkerProperties workerProperties;
    private final Credentials credentials;

    public RelayerTransactionSender(
            RelayerChainGateway gateway,
            Eip1559TransactionSigner signer,
            CreateBatchCallEncoder createBatchEncoder,
            CompleteStageCallEncoder completeStageEncoder,
            CommandSubmissionRepository submissionRepository,
            TransactionAttemptRepository attemptRepository,
            ChainProperties chainProperties,
            RelayerProperties relayerProperties,
            RelayerWorkerProperties workerProperties,
            Credentials credentials) {
        this.gateway = gateway;
        this.signer = signer;
        this.createBatchEncoder = createBatchEncoder;
        this.completeStageEncoder = completeStageEncoder;
        this.submissionRepository = submissionRepository;
        this.attemptRepository = attemptRepository;
        this.chainProperties = chainProperties;
        this.relayerProperties = relayerProperties;
        this.workerProperties = workerProperties;
        this.credentials = credentials;
    }

    /**
     * Signs, persists and broadcasts one READY_TO_SUBMIT command. The caller must hold the lease.
     * A retryable exception leaves a persisted SUBMITTED attempt for recovery; it never starts a new
     * operation or changes the nonce.
     */
    public synchronized TransactionAttempt send(
            StageCommand command,
            String stationSignature,
            String leaseOwner,
            Instant now) {
        if (command == null || command.status() != CommandStatus.READY_TO_SUBMIT) {
            throw new IllegalArgumentException("a READY_TO_SUBMIT command is required");
        }
        String calldata = calldata(command, stationSignature);
        String from = credentials.getAddress();

        BigInteger nonce = gateway.pendingNonce(from);
        BigInteger priorityFee = RelayerFeePolicy.effectivePriorityFee(
                gateway.maxPriorityFeePerGas(), relayerProperties.minTipGwei());
        BigInteger maxFee = RelayerFeePolicy.maxFee(gateway.latestBaseFeePerGas(), priorityFee);
        BigInteger estimated = gateway.estimateGas(from, chainProperties.contractAddress(), calldata);
        BigInteger gasLimit = estimated
                .multiply(BigInteger.valueOf(workerProperties.gasMarginPercent()))
                .add(BigInteger.valueOf(99))
                .divide(BigInteger.valueOf(100));

        SignedTransaction signed = signer.sign(new Eip1559Transaction(
                chainProperties.chainId(),
                nonce,
                gasLimit,
                priorityFee,
                maxFee,
                chainProperties.contractAddress(),
                BigInteger.ZERO,
                calldata), credentials);

        TransactionAttempt attempt = new TransactionAttempt(
                UUID.randomUUID(),
                command.id(),
                from.toLowerCase(java.util.Locale.ROOT),
                new BigDecimal(nonce),
                signed.transactionHash(),
                signed.rawTransaction(),
                Hash.sha3(calldata),
                new BigDecimal(priorityFee),
                new BigDecimal(maxFee),
                new BigDecimal(gasLimit),
                null,
                null,
                null,
                null,
                now);

        submissionRepository.reserveAndMarkSubmitted(attempt, leaseOwner, now);

        String returnedHash = gateway.sendRawTransaction(signed.rawTransaction());
        if (!signed.transactionHash().equalsIgnoreCase(returnedHash)) {
            throw new RelayerTransactionException(
                    true, "TX_HASH_MISMATCH",
                    "RPC returned a different transaction hash; reconcile by the stored attempt", null);
        }
        return attemptRepository.update(attempt.withBroadcastAt(now));
    }

    private String calldata(StageCommand command, String stationSignature) {
        return switch (command.type()) {
            case CREATE_BATCH -> createBatchEncoder.encode(command.batchNumber());
            case STAGE -> completeStageEncoder.encode(new CompleteStageCall(
                    command.batchId(),
                    command.stageId(),
                    command.reportHash(),
                    Boolean.TRUE.equals(command.qaPassed()),
                    command.deadline(),
                    command.cid(),
                    stationSignature,
                    0L,
                    ""));
        };
    }
}
