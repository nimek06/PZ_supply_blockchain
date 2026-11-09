package pl.pz.supplyblockchain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3jConfig {

    @Bean
    Web3j web3j(RpcProperties rpcProperties) {
        return Web3j.build(new HttpService(rpcProperties.url()));
    }
}
