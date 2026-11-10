package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import pl.pz.supplyblockchain.application.ports.StationRegistryException;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StationIdentityVerification;
import pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason;
import pl.pz.supplyblockchain.domain.StationIdentityVerification.Status;
import pl.pz.supplyblockchain.domain.StationRegistration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T010 against a real node prepared by {@code contracts/script/deploy-local.sh} (station ST-001 registered
 * with the fixture's {@code registryCall}). Run:
 * {@code TEST_RPC_URL=http://127.0.0.1:8545 ./mvnw verify -Dit.test=Web3jStationRegistryIT}.
 * Optional {@code TEST_CONTRACT_ADDRESS} (default: the contract bound in the certificate fixture).
 */
@EnabledIfEnvironmentVariable(named = "TEST_RPC_URL", matches = ".+")
class Web3jStationRegistryIT {

    private static final long LOCAL_CHAIN_ID = 31337;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static Web3j web3j;
    private static JsonNode fixture;
    private static DeploymentId deployment;

    @BeforeAll
    static void connect() throws IOException {
        try (InputStream in = Web3jStationRegistryIT.class.getResourceAsStream("/fixtures/station-certificate.json")) {
            fixture = MAPPER.readTree(in);
        }
        web3j = Web3j.build(new HttpService(System.getenv("TEST_RPC_URL")));
        long chainId = web3j.ethChainId().send().getChainId().longValueExact();
        assertThat(chainId)
                .as("TEST_RPC_URL must point to the local Anvil prepared by contracts/script/deploy-local.sh")
                .isEqualTo(LOCAL_CHAIN_ID);
        String contract = Optional.ofNullable(System.getenv("TEST_CONTRACT_ADDRESS"))
                .filter(value -> !value.isBlank())
                .orElse(fixture.at("/certificate/payload/verifyingContract").asString());
        deployment = new DeploymentId(chainId, contract.toLowerCase(Locale.ROOT));
    }

    @AfterAll
    static void shutdown() {
        if (web3j != null) {
            web3j.shutdown();
        }
    }

    @Test
    void registeredStationMatchesFixtureRegistryCall() {
        StationRegistration station = registry().getStation(fixture.at("/registryCall/station").asString());

        assertThat(station.active()).isTrue();
        assertThat(station.stationId()).isEqualTo(fixture.at("/registryCall/stationId").asString());
        assertThat(station.expiresAt()).isEqualTo(fixture.at("/registryCall/expiresAt").asLong());
        assertThat(station.stageMask()).isEqualTo(fixture.at("/registryCall/stageMask").asLong());
    }

    @Test
    void unknownAddressWasNeverRegistered() {
        StationRegistration station = registry().getStation("0x000000000000000000000000000000000000dEaD");

        assertThat(station.active()).isFalse();
        assertThat(station.everRegistered()).isFalse();
    }

    @Test
    void addressWithoutContractIsInvalidResponse() {
        // e.g. a CONTRACT_ADDRESS left over from before an Anvil restart
        Web3jStationRegistry stale = new Web3jStationRegistry(
                web3j, new DeploymentId(LOCAL_CHAIN_ID, "0x000000000000000000000000000000000000bEEF"));

        assertThatThrownBy(() -> stale.getStation(fixture.at("/registryCall/station").asString()))
                .isInstanceOfSatisfying(StationRegistryException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(StationRegistryException.Reason.INVALID_RESPONSE));
    }

    @Test
    void certificateFixtureIsActiveAgainstDeployedRegistry() {
        JsonNode payload = fixture.at("/certificate/payload");
        Assumptions.assumeTrue(
                payload.get("verifyingContract").asString().equalsIgnoreCase(deployment.contractAddress()),
                "the certificate is bound to " + payload.get("verifyingContract").asString());
        // inside the certificate window: the fixture's issuedAt may still be in the future
        Clock clock = Clock.fixed(Instant.ofEpochSecond(payload.get("issuedAt").asLong() + 600), ZoneOffset.UTC);
        Eip712StationCertificateVerifier verifier = new Eip712StationCertificateVerifier(
                new StationCertificatePolicy(deployment, Set.of(payload.get("issuerAddress").asString())),
                registry(),
                clock);

        StationIdentityVerification result = verifier.verify(MAPPER.writeValueAsString(fixture.get("certificate")));

        assertThat(result.reason()).isEqualTo(Reason.OK);
        assertThat(result.status()).isEqualTo(Status.ACTIVE);
    }

    private static Web3jStationRegistry registry() {
        return new Web3jStationRegistry(web3j, deployment);
    }
}
