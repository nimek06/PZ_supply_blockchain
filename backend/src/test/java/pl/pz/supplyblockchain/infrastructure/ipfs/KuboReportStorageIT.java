package pl.pz.supplyblockchain.infrastructure.ipfs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.pz.supplyblockchain.application.ports.StoredReport;
import pl.pz.supplyblockchain.config.KuboProperties;
import pl.pz.supplyblockchain.support.SignatureVectorFixture;
import tools.jackson.databind.json.JsonMapper;

/** Runs against a real Kubo node, the same image as in infrastructure/local/compose.yaml. */
@Testcontainers(disabledWithoutDocker = true)
class KuboReportStorageIT {

    @Container
    private static final GenericContainer<?> KUBO = new GenericContainer<>(DockerImageName.parse("ipfs/kubo:v0.32.1"))
            .withCommand("daemon", "--offline")
            .withExposedPorts(5001)
            .waitingFor(Wait.forLogMessage(".*Daemon is ready.*\\n", 1).withStartupTimeout(Duration.ofSeconds(60)));

    private final JsonMapper mapper = JsonMapper.builder().build();

    private KuboReportStorage storage() {
        String apiUrl = "http://" + KUBO.getHost() + ":" + KUBO.getMappedPort(5001);
        return new KuboReportStorage(
                new KuboProperties(apiUrl, Duration.ofSeconds(2), Duration.ofSeconds(10), 65536), mapper);
    }

    @Test
    void storesVectorReportAsRawCidV1() throws Exception {
        byte[] report = SignatureVectorFixture.load(mapper).canonicalReportJcs().getBytes(StandardCharsets.UTF_8);

        StoredReport first = storage().store(report);
        StoredReport again = storage().store(report);

        assertThat(first.cid()).startsWith("bafkrei");
        assertThat(CidValidator.isValid(first.cid())).isTrue();
        assertThat(again.cid()).isEqualTo(first.cid());
    }

    @Test
    void differentBytesGiveDifferentCid() throws Exception {
        String jcs = SignatureVectorFixture.load(mapper).canonicalReportJcs();

        String cid = storage().store(jcs.getBytes(StandardCharsets.UTF_8)).cid();
        String pretty = storage().store(jcs.replace(",", ", ").getBytes(StandardCharsets.UTF_8)).cid();

        assertThat(pretty).isNotEqualTo(cid);
    }
}
