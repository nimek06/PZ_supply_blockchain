package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.StationRegistry;
import pl.pz.supplyblockchain.application.ports.StationRegistryException;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StationIdentityVerification;
import pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason;
import pl.pz.supplyblockchain.domain.StationIdentityVerification.Status;
import pl.pz.supplyblockchain.domain.StationRegistration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T010: station certificate verification on {@code shared/fixtures/station-certificate.json}.
 * Re-signed variants use public Anvil development keys and web3j's own EIP-712 encoder,
 * so the signing side does not reuse the code under test.
 */
class Eip712StationCertificateVerifierTest {

    private static final String ISSUER_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String OTHER_KEY = "0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a";
    private static final String ISSUER = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    private static final String OTHER = "0x3c44cdddb6a900fa2b585dd299e03d12fa4293bc";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String STATION_CHECKSUM = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
    private static final String STATION_ID = "0x071dc5a525d6ea42c4b169703ea5c1776b953512942daac912791c213858f46c";
    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final long ISSUED_AT = 1_800_000_000L;
    private static final long EXPIRES_AT = 1_831_536_000L;
    private static final long NOW = ISSUED_AT + 600;
    private static final DeploymentId LOCAL = new DeploymentId(31337, CONTRACT);
    private static final BigInteger SECP256K1_N =
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private JsonNode fixture;
    private ObjectNode certificate;
    private FakeRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        fixture = loadFixture("station-certificate.json");
        certificate = (ObjectNode) fixture.get("certificate").deepCopy();
        registry = new FakeRegistry();
        registry.register(STATION, new StationRegistration(true, STATION_ID, EXPIRES_AT, 0x0F));
    }

    // ------------------------------------------------------------ valid certificate

    @Test
    void validCertificateIsActiveAndMatchesSharedVector() {
        StationIdentityVerification result = verifier().verify(json());

        assertThat(result.accepted()).isTrue();
        assertThat(result.reason()).isEqualTo(Reason.OK);
        assertThat(result.status()).isEqualTo(Status.ACTIVE);
        assertThat(result.payloadHash()).isEqualTo(fixture.at("/certificate/payloadHash").asString());
        assertThat(result.digest()).isEqualTo(fixture.at("/certificateSignature/digest").asString());
        assertThat(result.payload().stationAddress()).isEqualTo(STATION);
        assertThat(result.payload().stationId()).isEqualTo(STATION_ID);
        assertThat(result.payload().allowedStageIds()).containsExactly(0, 1, 2, 3);
        assertThat((long) result.payload().stageMask()).isEqualTo(fixture.at("/registryCall/stageMask").asLong());
        assertThat(registry.calls).containsExactly(STATION);
    }

    @Test
    void sharedVectorDigestMatchesIndependentEip712Encoder() throws Exception {
        String payloadHash = fixture.at("/certificate/payloadHash").asString();

        assertThat(Numeric.toHexString(independentDigest(31337, CONTRACT, payloadHash)))
                .isEqualTo(fixture.at("/certificateSignature/digest").asString());
        assertThat(jcsHash(certificate.get("payload"))).isEqualTo(payloadHash);
        assertThat(new String(new JsonCanonicalizer(MAPPER.writeValueAsString(certificate.get("payload")))
                .getEncodedUTF8(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(fixture.get("canonicalPayloadJcs").asString());
    }

    @Test
    void hashIsComputedOverJcsSoFormattingAndKeyOrderDoNotMatter() {
        ObjectNode reordered = MAPPER.createObjectNode();
        List<String> keys = new ArrayList<>();
        payload().properties().forEach(entry -> keys.add(entry.getKey()));
        for (int i = keys.size() - 1; i >= 0; i--) {
            reordered.set(keys.get(i), payload().get(keys.get(i)));
        }
        certificate.set("payload", reordered);

        String pretty = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(certificate);

        assertThat(verifier().verify(pretty).reason()).isEqualTo(Reason.OK);
    }

    @Test
    void reportSignedByCertifiedStationIsAcceptedRegardlessOfAddressCase() {
        StationIdentityVerification result = verifier().verifyForReport(json(), STATION_CHECKSUM, 2);

        assertThat(result.accepted()).isTrue();
    }

    // ------------------------------------------------------------ report binding

    @Test
    void reportSignedByAnotherAddressIsRejectedAlthoughStationIsActive() {
        StationIdentityVerification result = verifier().verifyForReport(json(), OTHER, 0);

        assertThat(result.reason()).isEqualTo(Reason.SIGNER_MISMATCH);
        assertThat(result.status()).isEqualTo(Status.ACTIVE);
        assertThat(result.accepted()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 4, 31})
    void stageOutsideTheProtocolIsNotAllowed(int stageId) {
        assertThat(verifier().verifyForReport(json(), STATION, stageId).reason()).isEqualTo(Reason.STAGE_NOT_ALLOWED);
    }

    @Test
    void stageOutsideAllowedStageIdsIsRejectedForAnOtherwiseActiveStation() throws Exception {
        payload().set("allowedStageIds", MAPPER.createArrayNode().add(0).add(1));
        resign(ISSUER_KEY);
        registry.register(STATION, new StationRegistration(true, STATION_ID, EXPIRES_AT, 0x03));

        assertThat(verifier().verifyForReport(json(), STATION, 1).reason()).isEqualTo(Reason.OK);
        StationIdentityVerification result = verifier().verifyForReport(json(), STATION, 2);
        assertThat(result.reason()).isEqualTo(Reason.STAGE_NOT_ALLOWED);
        assertThat(result.status()).isEqualTo(Status.ACTIVE);
    }

    // ------------------------------------------------------------ validity window

    @Test
    void certificateExpiresExactlyAtExpiresAt() {
        assertThat(verifier(EXPIRES_AT - 1).verify(json()).reason()).isEqualTo(Reason.OK);

        StationIdentityVerification expired = verifier(EXPIRES_AT).verify(json());
        assertThat(expired.reason()).isEqualTo(Reason.EXPIRED);
        assertThat(expired.status()).isEqualTo(Status.EXPIRED);
        assertThat(expired.code()).isEqualTo("expired");
    }

    @Test
    void certificateIsNotValidBeforeIssuedAt() {
        assertThat(verifier(ISSUED_AT).verify(json()).reason()).isEqualTo(Reason.OK);
        assertThat(verifier(ISSUED_AT - 1).verify(json()).reason()).isEqualTo(Reason.NOT_YET_VALID);
    }

    @Test
    void invalidCertificatesDoNotQueryTheRegistry() {
        verifier(EXPIRES_AT).verify(json());
        verifier(NOW, OTHER).verify(json());

        assertThat(registry.calls).isEmpty();
    }

    // ------------------------------------------------------------ on-chain registry

    @Test
    void revokedStationIsRejected() {
        registry.register(STATION, new StationRegistration(false, STATION_ID, EXPIRES_AT, 0x0F));

        StationIdentityVerification result = verifier().verify(json());

        assertThat(result.reason()).isEqualTo(Reason.REVOKED);
        assertThat(result.status()).isEqualTo(Status.REVOKED);
    }

    @Test
    void validCertificateWithoutOnChainRegistrationIsPendingActivation() {
        registry.stations.clear();

        StationIdentityVerification result = verifier().verify(json());

        assertThat(result.reason()).isEqualTo(Reason.NOT_REGISTERED);
        assertThat(result.status()).isEqualTo(Status.PENDING_ACTIVATION);
    }

    @Test
    void registryEntryForAnotherAddressDoesNotActivateTheCertificate() {
        registry.stations.clear();
        registry.register(OTHER, new StationRegistration(true, STATION_ID, EXPIRES_AT, 0x0F));

        assertThat(verifier().verify(json()).reason()).isEqualTo(Reason.NOT_REGISTERED);
        assertThat(registry.calls).containsExactly(STATION);
    }

    static Stream<Arguments> registryMismatches() {
        String otherId = "0x" + "11".repeat(32);
        return Stream.of(
                Arguments.of("stationId", new StationRegistration(true, otherId, EXPIRES_AT, 0x0F)),
                Arguments.of("expiresAt", new StationRegistration(true, STATION_ID, EXPIRES_AT + 1, 0x0F)),
                Arguments.of("stageMask", new StationRegistration(true, STATION_ID, EXPIRES_AT, 0x07)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registryMismatches")
    void activeRegistrationThatDiffersFromCertificateIsAMismatch(String field, StationRegistration onChain) {
        registry.register(STATION, onChain);

        StationIdentityVerification result = verifier().verify(json());

        assertThat(result.reason()).isEqualTo(Reason.REGISTRY_MISMATCH);
        assertThat(result.status()).isEqualTo(Status.REGISTRY_MISMATCH);
        assertThat(result.detail()).contains(field);
    }

    @Test
    void registryFailureIsAnAvailabilityErrorNotAVerdict() {
        registry.failure = new StationRegistryException(
                StationRegistryException.Reason.UNAVAILABLE, "connection refused", null);

        assertThatThrownBy(() -> verifier().verify(json()))
                .isInstanceOf(StationRegistryException.class)
                .satisfies(error -> assertThat(((StationRegistryException) error).reason().retryable()).isTrue());
    }

    // ------------------------------------------------------------ issuer and deployment

    @Test
    void validSignatureFromIssuerOutsideAllowlistIsUntrusted() throws Exception {
        payload().put("issuerAddress", OTHER);
        resign(OTHER_KEY);

        StationIdentityVerification result = verifier().verify(json());

        assertThat(result.reason()).isEqualTo(Reason.UNTRUSTED_ISSUER);
        assertThat(result.code()).isEqualTo("untrusted_issuer");
        assertThat(verifier(NOW, ISSUER, OTHER).verify(json()).reason()).isEqualTo(Reason.OK);
    }

    @Test
    void sharedCertificateIsUntrustedWhenItsIssuerIsNotAllowlisted() {
        assertThat(verifier(NOW, OTHER).verify(json()).reason()).isEqualTo(Reason.UNTRUSTED_ISSUER);
    }

    @Test
    void certificateClaimingTrustedIssuerButSignedByAnotherKeyIsRejected() throws Exception {
        resign(OTHER_KEY);

        StationIdentityVerification result = verifier(NOW, ISSUER, OTHER).verify(json());

        assertThat(result.reason()).isEqualTo(Reason.INVALID_ISSUER_SIGNATURE);
        assertThat(result.detail()).contains(OTHER);
    }

    @Test
    void certificateForAnotherDeploymentIsRejected() throws Exception {
        Eip712StationCertificateVerifier amoy = new Eip712StationCertificateVerifier(
                new StationCertificatePolicy(new DeploymentId(80002, CONTRACT), Set.of(ISSUER)), registry, clock(NOW));
        Eip712StationCertificateVerifier otherContract = new Eip712StationCertificateVerifier(
                new StationCertificatePolicy(new DeploymentId(31337, OTHER), Set.of(ISSUER)), registry, clock(NOW));

        assertThat(amoy.verify(json()).reason()).isEqualTo(Reason.WRONG_DEPLOYMENT);
        assertThat(otherContract.verify(json()).reason()).isEqualTo(Reason.WRONG_DEPLOYMENT);

        payload().put("chainId", 80002);
        resign(ISSUER_KEY);
        assertThat(verifier().verify(json()).reason()).isEqualTo(Reason.WRONG_DEPLOYMENT);
        assertThat(amoy.verify(json()).reason()).isEqualTo(Reason.OK);
    }

    // ------------------------------------------------------------ tampering

    static Stream<Arguments> payloadTampering() {
        return Stream.of(
                Arguments.of("stationAddress", (Consumer<ObjectNode>) p -> p.put("stationAddress", OTHER)),
                Arguments.of("expiresAt", (Consumer<ObjectNode>) p -> p.put("expiresAt", EXPIRES_AT + 86_400)),
                Arguments.of("issuedAt", (Consumer<ObjectNode>) p -> p.put("issuedAt", ISSUED_AT - 1)),
                Arguments.of("stationId", (Consumer<ObjectNode>) p -> p.put("stationId", "0x" + "22".repeat(32))),
                Arguments.of("certificateId",
                        (Consumer<ObjectNode>) p -> p.put("certificateId", "0x" + "33".repeat(32))),
                Arguments.of("allowedStageIds",
                        (Consumer<ObjectNode>) p -> p.set("allowedStageIds", MAPPER.createArrayNode().add(0))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloadTampering")
    void changedPayloadNoLongerMatchesPayloadHash(String field, Consumer<ObjectNode> tamper) {
        tamper.accept(payload());

        assertThat(verifier().verify(json()).reason()).isEqualTo(Reason.PAYLOAD_HASH_MISMATCH);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloadTampering")
    void recomputedPayloadHashStillNeedsTheIssuerSignature(String field, Consumer<ObjectNode> tamper) {
        tamper.accept(payload());
        certificate.put("payloadHash", jcsHash(payload()));

        assertThat(verifier().verify(json()).reason()).isEqualTo(Reason.INVALID_ISSUER_SIGNATURE);
    }

    static Stream<Arguments> signatureTampering() {
        return Stream.of(
                Arguments.of("flipped r byte", (Function<byte[], byte[]>) sig -> flip(sig, 5)),
                Arguments.of("flipped s byte", (Function<byte[], byte[]>) sig -> flip(sig, 40)),
                Arguments.of("flipped v", (Function<byte[], byte[]>) sig -> {
                    sig[64] = (byte) (sig[64] == 27 ? 28 : 27);
                    return sig;
                }),
                Arguments.of("invalid v", (Function<byte[], byte[]>) sig -> {
                    sig[64] = 29;
                    return sig;
                }),
                Arguments.of("high-s twin", (Function<byte[], byte[]>) Eip712StationCertificateVerifierTest::highS),
                Arguments.of("zero r", (Function<byte[], byte[]>) sig -> {
                    java.util.Arrays.fill(sig, 0, 32, (byte) 0);
                    return sig;
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("signatureTampering")
    void tamperedIssuerSignatureIsRejected(String name, Function<byte[], byte[]> tamper) {
        byte[] signature = Numeric.hexStringToByteArray(certificate.get("signature").asString());
        certificate.put("signature", Numeric.toHexString(tamper.apply(signature)));

        StationIdentityVerification result = verifier().verify(json());

        assertThat(result.reason()).isEqualTo(Reason.INVALID_ISSUER_SIGNATURE);
        assertThat(result.status()).isEqualTo(Status.INVALID);
    }

    // ------------------------------------------------------------ format v1

    static Stream<Arguments> malformedCertificates() {
        return Stream.of(
                malformed("not JSON", c -> "not json"),
                malformed("empty string", c -> ""),
                malformed("JSON array", c -> "[" + write(c) + "]"),
                malformed("trailing tokens", c -> write(c) + " {}"),
                malformed("oversized", c -> write(c) + " ".repeat(Eip712StationCertificateVerifier.MAX_CERTIFICATE_BYTES)),
                malformed("duplicate envelope key", c -> write(c).replaceFirst("\\{", "{\"certificateVersion\":1,")),
                malformed("duplicate payload key", c -> write(c).replace(
                        "\"payload\":{", "\"payload\":{\"stationAddress\":\"" + OTHER + "\",")),
                malformed("certificateVersion 2", c -> write(c.put("certificateVersion", 2))),
                malformed("certificateVersion string", c -> write(c.put("certificateVersion", "1"))),
                malformed("missing payloadHash", c -> write(remove(c, "payloadHash"))),
                malformed("extra envelope field", c -> write(c.put("note", "x"))),
                malformed("payload not object", c -> write(c.put("payload", "x"))),
                malformed("payloadHash uppercase", c -> write(c.put("payloadHash",
                        c.get("payloadHash").asString().toUpperCase().replace("0X", "0x")))),
                malformed("signature 64 bytes", c -> write(c.put("signature",
                        c.get("signature").asString().substring(0, 130)))),
                malformed("schemaVersion 2", c -> write(p(c).put("schemaVersion", 2), c)),
                malformed("extra payload field", c -> write(p(c).put("organization", "ACME"), c)),
                malformed("missing allowedStageIds", c -> write(remove(p(c), "allowedStageIds"), c)),
                malformed("checksummed stationAddress", c -> write(p(c).put("stationAddress", STATION_CHECKSUM), c)),
                malformed("zero stationAddress", c -> write(p(c).put("stationAddress", "0x" + "0".repeat(40)), c)),
                malformed("zero issuerAddress", c -> write(p(c).put("issuerAddress", "0x" + "0".repeat(40)), c)),
                malformed("short stationId", c -> write(p(c).put("stationId", "0x1234"), c)),
                malformed("chainId 0", c -> write(p(c).put("chainId", 0), c)),
                malformed("chainId string", c -> write(p(c).put("chainId", "31337"), c)),
                malformed("chainId float", c -> write(p(c).put("chainId", 31337.0), c)),
                malformed("issuedAt negative", c -> write(p(c).put("issuedAt", -1), c)),
                malformed("expiresAt above safe integer", c -> write(p(c).put("expiresAt", 9_007_199_254_740_992L), c)),
                malformed("expiresAt equals issuedAt", c -> write(p(c).put("expiresAt", ISSUED_AT), c)),
                malformed("allowedStageIds empty", c -> write(p(c).set("allowedStageIds", MAPPER.createArrayNode()), c)),
                malformed("allowedStageIds unsorted", c -> write(p(c).set("allowedStageIds", ids(1, 0)), c)),
                malformed("allowedStageIds duplicate", c -> write(p(c).set("allowedStageIds", ids(0, 0)), c)),
                malformed("allowedStageIds 4", c -> write(p(c).set("allowedStageIds", ids(0, 4)), c)),
                malformed("allowedStageIds negative", c -> write(p(c).set("allowedStageIds", ids(-1, 0)), c)),
                malformed("allowedStageIds not array", c -> write(p(c).put("allowedStageIds", 15), c)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedCertificates")
    void certificateOutsideFormatV1IsMalformed(String name, Function<ObjectNode, String> mutate) {
        StationIdentityVerification result = verifier().verify(mutate.apply(certificate.deepCopy()));

        assertThat(result.reason()).isEqualTo(Reason.MALFORMED);
        assertThat(result.status()).isEqualTo(Status.INVALID);
        assertThat(result.payload()).isNull();
        assertThat(registry.calls).isEmpty();
    }

    @Test
    void missingCertificateIsMalformed() {
        assertThat(verifier().verify(null).reason()).isEqualTo(Reason.MALFORMED);
    }

    // ------------------------------------------------------------ policy

    @Test
    void policyRequiresANonEmptyAllowlistOfAddresses() {
        assertThatThrownBy(() -> new StationCertificatePolicy(LOCAL, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StationCertificatePolicy(LOCAL, Set.of("0x" + "0".repeat(40))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StationCertificatePolicy(LOCAL, Set.of("issuer")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new StationCertificatePolicy(LOCAL, Set.of("0xF39Fd6e51aad88F6F4ce6aB8827279cffFb92266"))
                .trusts(ISSUER)).isTrue();
    }

    // ------------------------------------------------------------ shared vectors

    /**
     * Every {@code station-certificate*.json} copy with an {@code expectedVerification} block (negative
     * vectors from the simulator) is replayed with its {@code checkedAt} and {@code trustedIssuers}.
     * The base fixture is expected to be accepted.
     */
    @TestFactory
    Stream<DynamicTest> sharedCertificateVectorsHaveTheExpectedOutcome() throws IOException, URISyntaxException {
        Path directory = Path.of(getClass().getResource("/fixtures").toURI());
        List<Path> vectors;
        try (Stream<Path> files = Files.list(directory)) {
            vectors = files.filter(path -> path.getFileName().toString().matches("station-certificate.*\\.json"))
                    .sorted()
                    .toList();
        }
        assertThat(vectors).isNotEmpty();
        return vectors.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
            JsonNode vector = MAPPER.readTree(path.toFile());
            JsonNode expected = vector.path("expectedVerification");
            JsonNode payload = vector.at("/certificate/payload");
            long now = expected.has("checkedAt") ? expected.get("checkedAt").asLong() : NOW;
            Set<String> trusted = new HashSet<>();
            expected.path("trustedIssuers").forEach(issuer -> trusted.add(issuer.asString()));
            if (trusted.isEmpty()) {
                trusted.add(ISSUER);
            }
            FakeRegistry echo = new FakeRegistry();
            long mask = 0;
            for (JsonNode stageId : payload.get("allowedStageIds")) {
                mask |= 1L << stageId.asInt();
            }
            echo.register(payload.get("stationAddress").asString(), new StationRegistration(
                    true, payload.get("stationId").asString(), payload.get("expiresAt").asLong(), mask));
            Eip712StationCertificateVerifier verifier = new Eip712StationCertificateVerifier(
                    new StationCertificatePolicy(LOCAL, trusted), echo, clock(now));

            StationIdentityVerification result = verifier.verify(write((ObjectNode) vector.get("certificate")));

            assertThat(result.accepted()).isEqualTo(expected.path("accepted").asBoolean(true));
            assertThat(result.code()).isEqualTo(expected.path("reason").asString("ok"));
        }));
    }

    // ------------------------------------------------------------ helpers

    private Eip712StationCertificateVerifier verifier() {
        return verifier(NOW);
    }

    private Eip712StationCertificateVerifier verifier(long now, String... trustedIssuers) {
        Set<String> trusted = trustedIssuers.length == 0 ? Set.of(ISSUER) : Set.of(trustedIssuers);
        return new Eip712StationCertificateVerifier(new StationCertificatePolicy(LOCAL, trusted), registry, clock(now));
    }

    private static Clock clock(long epochSeconds) {
        return Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    private ObjectNode payload() {
        return (ObjectNode) certificate.get("payload");
    }

    private String json() {
        return write(certificate);
    }

    /** Recomputes payloadHash and signs it with {@code privateKey} through web3j's EIP-712 encoder. */
    private void resign(String privateKey) throws Exception {
        String payloadHash = jcsHash(payload());
        byte[] digest = independentDigest(payload().get("chainId").asLong(),
                payload().get("verifyingContract").asString(), payloadHash);
        Sign.SignatureData signature = Sign.signMessage(digest, ECKeyPair.create(Numeric.toBigInt(privateKey)), false);
        byte[] bytes = new byte[65];
        System.arraycopy(signature.getR(), 0, bytes, 0, 32);
        System.arraycopy(signature.getS(), 0, bytes, 32, 32);
        bytes[64] = signature.getV()[0];
        certificate.put("payloadHash", payloadHash);
        certificate.put("signature", Numeric.toHexString(bytes));
    }

    private static byte[] independentDigest(long chainId, String verifyingContract, String payloadHash)
            throws IOException {
        String typedData = """
                {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},
                {"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
                "StationCertificate":[{"name":"payloadHash","type":"bytes32"}]},
                "primaryType":"StationCertificate",
                "domain":{"name":"SupplyChainStationRegistry","version":"1","chainId":%d,"verifyingContract":"%s"},
                "message":{"payloadHash":"%s"}}""".formatted(chainId, verifyingContract, payloadHash);
        return new StructuredDataEncoder(typedData).hashStructuredData();
    }

    private static String jcsHash(JsonNode payload) {
        try {
            return Numeric.toHexString(Hash.sha3(new JsonCanonicalizer(MAPPER.writeValueAsString(payload))
                    .getEncodedUTF8()));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] flip(byte[] signature, int index) {
        signature[index] ^= 0x01;
        return signature;
    }

    private static byte[] highS(byte[] signature) {
        BigInteger s = new BigInteger(1, java.util.Arrays.copyOfRange(signature, 32, 64));
        System.arraycopy(Numeric.toBytesPadded(SECP256K1_N.subtract(s), 32), 0, signature, 32, 32);
        signature[64] = (byte) (signature[64] == 27 ? 28 : 27);
        return signature;
    }

    private static Arguments malformed(String name, Function<ObjectNode, String> mutate) {
        return Arguments.of(name, mutate);
    }

    private static ObjectNode p(ObjectNode certificate) {
        return (ObjectNode) certificate.get("payload");
    }

    private static ObjectNode remove(ObjectNode node, String field) {
        node.remove(field);
        return node;
    }

    private static ArrayNode ids(int... values) {
        ArrayNode array = MAPPER.createArrayNode();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    private static String write(ObjectNode node) {
        return MAPPER.writeValueAsString(node);
    }

    /** Writes the whole certificate after a payload mutation ({@code payload} is only for chaining). */
    private static String write(ObjectNode payload, ObjectNode certificate) {
        return MAPPER.writeValueAsString(certificate);
    }

    private static JsonNode loadFixture(String name) throws IOException {
        try (InputStream input = Eip712StationCertificateVerifierTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            if (input == null) {
                throw new IllegalStateException("fixture " + name + " is missing");
            }
            return MAPPER.readTree(input);
        }
    }

    private static final class FakeRegistry implements StationRegistry {

        private final Map<String, StationRegistration> stations = new HashMap<>();
        private final List<String> calls = new ArrayList<>();
        private RuntimeException failure;

        void register(String address, StationRegistration registration) {
            stations.put(address, registration);
        }

        @Override
        public StationRegistration getStation(String stationAddress) {
            calls.add(stationAddress);
            if (failure != null) {
                throw failure;
            }
            return stations.getOrDefault(stationAddress,
                    new StationRegistration(false, StationRegistration.ZERO_STATION_ID, 0, 0));
        }
    }
}
