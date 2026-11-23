package pl.pz.supplyblockchain.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import org.web3j.protocol.Web3j;
import pl.pz.supplyblockchain.infrastructure.blockchain.ChainEventIndexer;
import pl.pz.supplyblockchain.infrastructure.persistence.JdbcChainEventStore;

@Configuration
public class ChainEventIndexerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "supplychain.indexer", name = "enabled", havingValue = "true")
    ChainEventIndexer chainEventIndexer(
            Web3j web3j,
            ChainProperties chainProperties,
            ChainIndexerProperties indexerProperties,
            JdbcChainEventStore eventStore,
            ObjectMapper objectMapper) {
        return new ChainEventIndexer(
                web3j, chainProperties, indexerProperties, eventStore, objectMapper);
    }
}
