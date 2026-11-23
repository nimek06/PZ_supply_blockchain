package pl.pz.supplyblockchain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.application.ports.BatchProjection;
import pl.pz.supplyblockchain.application.ports.BatchProjectionRepository;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.DomainException;

class BatchQueryServiceTest {

    private static final String BATCH_ID =
            "0xbd23cd342e8d6764ba34c70b9da72967ec708e76f1acf807f196c4557b5e635b";
    private static final String BATCH_NUMBER = "B-2026-0001";

    private final BatchProjectionRepository projectionRepository = mock(BatchProjectionRepository.class);
    private final BatchQueryService service = new BatchQueryService(
            projectionRepository,
            new ChainProperties(31337, "0x5fbdb2315678afecb367f032d93f642f64180aa3", "SupplyChain", "1", 3600));

    @Test
    void batchIdQueryUsesTheIndexedProjectionAndItsFreshness() {
        when(projectionRepository.findByBatchId(
                        serviceDeploymentId(), BATCH_ID))
                .thenReturn(Optional.of(new BatchProjection(BATCH_ID, BATCH_NUMBER, 1, false, 18, false)));

        var view = service.findByBatchId(BATCH_ID);

        assertThat(view.batchNumber()).isEqualTo(BATCH_NUMBER);
        assertThat(view.nextStage()).isEqualTo(1);
        assertThat(view.projection().lastIndexedBlock()).isEqualTo(18);
        assertThat(view.projection().stale()).isFalse();
    }

    @Test
    void completedBatchHasNoNextStage() {
        when(projectionRepository.findByBatchNumber(
                        serviceDeploymentId(), BATCH_NUMBER))
                .thenReturn(Optional.of(new BatchProjection(BATCH_ID, BATCH_NUMBER, 4, true, 22, false)));

        assertThat(service.findByBatchNumber(BATCH_NUMBER).nextStage()).isNull();
    }

    @Test
    void unknownBatchIsNotServedFromPendingCommands() {
        when(projectionRepository.findByBatchId(serviceDeploymentId(), BATCH_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByBatchId(BATCH_ID))
                .isInstanceOf(DomainException.class);
    }

    private pl.pz.supplyblockchain.domain.DeploymentId serviceDeploymentId() {
        return new pl.pz.supplyblockchain.domain.DeploymentId(
                31337, "0x5fbdb2315678afecb367f032d93f642f64180aa3");
    }
}
