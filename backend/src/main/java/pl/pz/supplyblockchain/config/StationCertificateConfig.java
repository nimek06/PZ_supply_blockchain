package pl.pz.supplyblockchain.config;

import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import pl.pz.supplyblockchain.application.ports.StationCertificateVerifier;
import pl.pz.supplyblockchain.application.ports.StationRegistry;
import pl.pz.supplyblockchain.infrastructure.blockchain.Eip712StationCertificateVerifier;
import pl.pz.supplyblockchain.infrastructure.blockchain.StationCertificatePolicy;
import pl.pz.supplyblockchain.infrastructure.blockchain.Web3jStationRegistry;

@Configuration
@EnableConfigurationProperties(StationCertificateProperties.class)
public class StationCertificateConfig {

    @Bean
    StationCertificatePolicy stationCertificatePolicy(
            ChainProperties chainProperties, StationCertificateProperties stationProperties) {
        return new StationCertificatePolicy(
                chainProperties.deploymentId(),
                Arrays.stream(stationProperties.trustedIssuers().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(HashSet::new)));
    }

    @Bean
    StationRegistry stationRegistry(Web3j web3j, ChainProperties chainProperties) {
        return new Web3jStationRegistry(web3j, chainProperties.deploymentId());
    }

    @Bean
    StationCertificateVerifier stationCertificateVerifier(
            StationCertificatePolicy policy, StationRegistry stationRegistry, Clock clock) {
        return new Eip712StationCertificateVerifier(policy, stationRegistry, clock);
    }
}
