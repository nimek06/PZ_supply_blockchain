package pl.pz.supplyblockchain.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.web3j.crypto.Credentials;
import pl.pz.supplyblockchain.application.ports.CommandLeaseRepository;
import pl.pz.supplyblockchain.application.ports.CommandLifecycleRepository;
import pl.pz.supplyblockchain.application.ports.CommandPayloadRepository;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.application.ports.CommandSubmissionRepository;
import pl.pz.supplyblockchain.application.ports.ConfirmedBlockTimeSource;
import pl.pz.supplyblockchain.application.ports.RelayerChainGateway;
import pl.pz.supplyblockchain.application.ports.ReportStorage;
import pl.pz.supplyblockchain.application.ports.StationCertificateRepository;
import pl.pz.supplyblockchain.application.ports.StationCertificateVerifier;
import pl.pz.supplyblockchain.application.ports.SubmittedRecoveryRepository;
import pl.pz.supplyblockchain.application.ports.TransactionAttemptRepository;
import pl.pz.supplyblockchain.infrastructure.blockchain.CompleteStageCallEncoder;
import pl.pz.supplyblockchain.infrastructure.blockchain.CreateBatchCallEncoder;
import pl.pz.supplyblockchain.infrastructure.blockchain.Eip1559TransactionSigner;
import pl.pz.supplyblockchain.infrastructure.blockchain.RelayerTransactionSender;
import pl.pz.supplyblockchain.infrastructure.queue.RelayerWorker;

@Configuration
@EnableScheduling
public class RelayerWorkerConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "supplychain.relayer.worker", name = "enabled", havingValue = "true")
    Credentials relayerCredentials(RelayerWalletProperties properties) {
        if (properties.privateKey() == null || properties.privateKey().isBlank()) {
            throw new IllegalStateException(
                    "supplychain.relayer.private-key is required when the relayer worker is enabled");
        }
        return Credentials.create(properties.privateKey());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "supplychain.relayer.worker", name = "enabled", havingValue = "true")
    RelayerTransactionSender relayerTransactionSender(
            RelayerChainGateway chainGateway,
            Eip1559TransactionSigner signer,
            CreateBatchCallEncoder createBatchEncoder,
            CompleteStageCallEncoder completeStageEncoder,
            CommandSubmissionRepository submissionRepository,
            TransactionAttemptRepository attemptRepository,
            ChainProperties chainProperties,
            RelayerProperties relayerProperties,
            RelayerWorkerProperties workerProperties,
            Credentials relayerCredentials) {
        return new RelayerTransactionSender(
                chainGateway,
                signer,
                createBatchEncoder,
                completeStageEncoder,
                submissionRepository,
                attemptRepository,
                chainProperties,
                relayerProperties,
                workerProperties,
                relayerCredentials);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "supplychain.relayer.worker", name = "enabled", havingValue = "true")
    RelayerWorker relayerWorker(
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
            RelayerWorkerProperties workerProperties,
            Clock clock) {
        return new RelayerWorker(
                leaseRepository,
                lifecycleRepository,
                payloadRepository,
                commandRepository,
                recoveryRepository,
                attemptRepository,
                reportStorage,
                certificateRepository,
                certificateVerifier,
                chainGateway,
                confirmedBlockTime,
                sender,
                workerProperties,
                clock);
    }
}
