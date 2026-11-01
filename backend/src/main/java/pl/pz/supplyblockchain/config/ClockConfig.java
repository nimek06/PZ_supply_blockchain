package pl.pz.supplyblockchain.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${supplychain.clock.fixed-now:}") String fixedNow) {
        if (fixedNow == null || fixedNow.isBlank()) {
            return Clock.systemUTC();
        }
        return Clock.fixed(Instant.ofEpochSecond(Long.parseLong(fixedNow)), ZoneOffset.UTC);
    }
}
