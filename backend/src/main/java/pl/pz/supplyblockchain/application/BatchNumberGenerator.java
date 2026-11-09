package pl.pz.supplyblockchain.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import pl.pz.supplyblockchain.application.ports.BatchSequenceRepository;
import pl.pz.supplyblockchain.domain.BatchNumber;
import pl.pz.supplyblockchain.domain.DeploymentId;

@Component
public class BatchNumberGenerator {

    private final BatchSequenceRepository sequenceRepository;
    private final Clock clock;

    public BatchNumberGenerator(BatchSequenceRepository sequenceRepository, Clock clock) {
        this.sequenceRepository = sequenceRepository;
        this.clock = clock;
    }

    public BatchNumber next(DeploymentId deploymentId) {
        int year = LocalDate.now(clock.withZone(ZoneOffset.UTC)).getYear();
        int sequence = sequenceRepository.nextValue(deploymentId, year);
        return BatchNumber.of(year, sequence);
    }
}
