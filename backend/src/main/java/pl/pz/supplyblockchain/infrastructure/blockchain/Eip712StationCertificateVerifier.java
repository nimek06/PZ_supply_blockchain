package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.erdtman.jcs.JsonCanonicalizer;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.StationCertificateVerifier;
import pl.pz.supplyblockchain.application.ports.StationRegistry;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.StationCertificatePayload;
import pl.pz.supplyblockchain.domain.StationIdentityVerification;
import pl.pz.supplyblockchain.domain.StationIdentityVerification.Reason;
import pl.pz.supplyblockchain.domain.StationRegistration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Station certificate verifier (T010, {@code docs/station-identity.md}). Order of checks, fail-closed:
 * format v1 -> keccak256(JCS(payload)) == payloadHash -> EIP-712 issuer signature recovers to issuerAddress
 * -> issuer on the allowlist -> chainId/verifyingContract of this deployment -> issuedAt <= now < expiresAt
 * -> {@code getStation(stationAddress)} active and equal to the certificate -> (report) signer and stage.
 * Mirrors {@code simulator/.../certificate.py::verify_certificate} plus the checks that need the backend.
 */
public class Eip712StationCertificateVerifier implements StationCertificateVerifier {

    static final String DOMAIN_TYPE =
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)";
    static final String DOMAIN_NAME = "SupplyChainStationRegistry";
    static final String DOMAIN_VERSION = "1";
    static final String CERTIFICATE_TYPE = "StationCertificate(bytes32 payloadHash)";
    static final int MAX_CERTIFICATE_BYTES = 16 * 1024;

    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final int STAGE_COUNT = 4;
    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-f]{40}$");
    private static final Pattern BYTES32 = Pattern.compile("^0x[0-9a-f]{64}$");
    private static final Pattern SIGNATURE = Pattern.compile("^0x[0-9a-f]{130}$");
    private static final String ZERO_ADDRESS = "0x" + "0".repeat(40);
    private static final Set<String> ENVELOPE_FIELDS =
            Set.of("certificateVersion", "payload", "payloadHash", "signature");
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
            "schemaVersion", "certificateId", "stationId", "stationAddress", "issuerAddress",
            "chainId", "verifyingContract", "issuedAt", "expiresAt", "allowedStageIds");

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final StationCertificatePolicy policy;
    private final StationRegistry registry;
    private final Clock clock;

    public Eip712StationCertificateVerifier(StationCertificatePolicy policy, StationRegistry registry, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public StationIdentityVerification verify(String certificateJson) {
        return check(certificateJson, null, 0);
    }

    @Override
    public StationIdentityVerification verifyForReport(
            String certificateJson, String reportSignerAddress, int stageId) {
        Objects.requireNonNull(reportSignerAddress, "reportSignerAddress");
        return check(certificateJson, reportSignerAddress.toLowerCase(Locale.ROOT), stageId);
    }

    private StationIdentityVerification check(String certificateJson, String reportSigner, int stageId) {
        Envelope envelope;
        try {
            envelope = parse(certificateJson);
        } catch (Malformed malformed) {
            return new StationIdentityVerification(Reason.MALFORMED, malformed.getMessage(), null, null, null);
        }
        StationCertificatePayload payload = envelope.payload();

        String payloadHash = envelope.computedPayloadHash();
        if (!payloadHash.equals(envelope.payloadHash())) {
            return result(Reason.PAYLOAD_HASH_MISMATCH,
                    "payloadHash " + envelope.payloadHash() + " does not match JCS payload " + payloadHash,
                    payload, payloadHash, null);
        }

        byte[] digest = issuerDigest(payload, payloadHash);
        String digestHex = Numeric.toHexString(digest);
        String recovered;
        try {
            recovered = EcdsaRecovery.recoverAddress(digest, envelope.signature());
        } catch (DomainException exception) {
            return result(Reason.INVALID_ISSUER_SIGNATURE, exception.getMessage(), payload, payloadHash, digestHex);
        }
        if (!recovered.equals(payload.issuerAddress())) {
            return result(Reason.INVALID_ISSUER_SIGNATURE,
                    "signature recovers to " + recovered + ", not issuerAddress", payload, payloadHash, digestHex);
        }
        if (!policy.trusts(payload.issuerAddress())) {
            return result(Reason.UNTRUSTED_ISSUER,
                    "issuer " + payload.issuerAddress() + " is not trusted for this deployment",
                    payload, payloadHash, digestHex);
        }
        if (payload.chainId() != policy.deployment().chainId()
                || !payload.verifyingContract().equals(policy.deployment().contractAddress())) {
            return result(Reason.WRONG_DEPLOYMENT,
                    "certificate is for chain " + payload.chainId() + " contract " + payload.verifyingContract(),
                    payload, payloadHash, digestHex);
        }

        long now = clock.instant().getEpochSecond();
        if (now < payload.issuedAt()) {
            return result(Reason.NOT_YET_VALID, "issuedAt " + payload.issuedAt() + " is after now " + now,
                    payload, payloadHash, digestHex);
        }
        if (now >= payload.expiresAt()) {
            return result(Reason.EXPIRED, "expiresAt " + payload.expiresAt() + " is not after now " + now,
                    payload, payloadHash, digestHex);
        }

        StationRegistration registration = Objects.requireNonNull(
                registry.getStation(payload.stationAddress()), "station registry returned null");
        if (!registration.active() && !registration.everRegistered()) {
            return result(Reason.NOT_REGISTERED, "station " + payload.stationAddress() + " is not registered on-chain",
                    payload, payloadHash, digestHex);
        }
        if (!registration.active()) {
            return result(Reason.REVOKED, "station " + payload.stationAddress() + " is revoked on-chain",
                    payload, payloadHash, digestHex);
        }
        String registryMismatch = registryMismatch(payload, registration);
        if (registryMismatch != null) {
            return result(Reason.REGISTRY_MISMATCH, registryMismatch, payload, payloadHash, digestHex);
        }

        if (reportSigner != null) {
            if (!reportSigner.equals(payload.stationAddress())) {
                return result(Reason.SIGNER_MISMATCH,
                        "report signed by " + reportSigner + ", certificate is for " + payload.stationAddress(),
                        payload, payloadHash, digestHex);
            }
            if (!payload.allowsStage(stageId)) {
                return result(Reason.STAGE_NOT_ALLOWED, "stage " + stageId + " is not in allowedStageIds",
                        payload, payloadHash, digestHex);
            }
        }
        return result(Reason.OK, "station certificate is active", payload, payloadHash, digestHex);
    }

    private static String registryMismatch(StationCertificatePayload payload, StationRegistration registration) {
        if (!payload.stationId().equals(registration.stationId())) {
            return "on-chain stationId " + registration.stationId() + " differs from certificate";
        }
        if (payload.expiresAt() != registration.expiresAt()) {
            return "on-chain expiresAt " + registration.expiresAt() + " differs from certificate";
        }
        if (payload.stageMask() != registration.stageMask()) {
            return "on-chain stageMask " + registration.stageMask() + " differs from certificate "
                    + payload.stageMask();
        }
        return null;
    }

    private static StationIdentityVerification result(
            Reason reason, String detail, StationCertificatePayload payload, String payloadHash, String digest) {
        return new StationIdentityVerification(reason, detail, payload, payloadHash, digest);
    }

    /** EIP-712 digest of {@code StationCertificate(bytes32 payloadHash)} in the domain named by the payload. */
    static byte[] issuerDigest(StationCertificatePayload payload, String payloadHash) {
        byte[] domainSeparator = keccak(concat(
                keccak(utf8(DOMAIN_TYPE)),
                keccak(utf8(DOMAIN_NAME)),
                keccak(utf8(DOMAIN_VERSION)),
                Numeric.toBytesPadded(BigInteger.valueOf(payload.chainId()), 32),
                Numeric.toBytesPadded(Numeric.toBigInt(payload.verifyingContract()), 32)));
        byte[] structHash = keccak(concat(
                keccak(utf8(CERTIFICATE_TYPE)),
                Numeric.hexStringToByteArray(payloadHash)));
        return keccak(concat(new byte[]{0x19, 0x01}, domainSeparator, structHash));
    }

    private Envelope parse(String certificateJson) {
        if (certificateJson == null) {
            throw new Malformed("certificate is required");
        }
        byte[] bytes = certificateJson.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CERTIFICATE_BYTES) {
            throw new Malformed("certificate exceeds " + MAX_CERTIFICATE_BYTES + " bytes");
        }
        JsonNode root;
        try {
            root = mapper.readTree(bytes);
        } catch (JacksonException exception) {
            throw new Malformed("certificate is not valid JSON");
        }
        requireExactFields(root, ENVELOPE_FIELDS, "certificate");
        if (safeInteger(root.get("certificateVersion"), "certificateVersion") != 1) {
            throw new Malformed("certificateVersion must be 1");
        }
        String payloadHash = text(root, "payloadHash", BYTES32);
        String signature = text(root, "signature", SIGNATURE);

        JsonNode payloadNode = root.get("payload");
        StationCertificatePayload payload = parsePayload(payloadNode);
        return new Envelope(payload, payloadHash, signature, jcsHash(payloadNode));
    }

    private static StationCertificatePayload parsePayload(JsonNode node) {
        requireExactFields(node, PAYLOAD_FIELDS, "payload");
        if (safeInteger(node.get("schemaVersion"), "schemaVersion") != 1) {
            throw new Malformed("payload.schemaVersion must be 1");
        }
        long chainId = safeInteger(node.get("chainId"), "chainId");
        if (chainId < 1) {
            throw new Malformed("payload.chainId must be positive");
        }
        long issuedAt = safeInteger(node.get("issuedAt"), "issuedAt");
        long expiresAt = safeInteger(node.get("expiresAt"), "expiresAt");
        if (expiresAt <= issuedAt) {
            throw new Malformed("payload.expiresAt must be after issuedAt");
        }
        return new StationCertificatePayload(
                1,
                text(node, "certificateId", BYTES32),
                text(node, "stationId", BYTES32),
                address(node, "stationAddress"),
                address(node, "issuerAddress"),
                chainId,
                address(node, "verifyingContract"),
                issuedAt,
                expiresAt,
                stageIds(node.get("allowedStageIds")));
    }

    private static List<Integer> stageIds(JsonNode node) {
        if (node == null || !node.isArray() || node.size() == 0) {
            throw new Malformed("payload.allowedStageIds must be a non-empty array");
        }
        List<Integer> stageIds = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            long stageId = safeInteger(element, "allowedStageIds[]");
            if (stageId >= STAGE_COUNT) {
                throw new Malformed("payload.allowedStageIds values must be in 0.." + (STAGE_COUNT - 1));
            }
            if (!stageIds.isEmpty() && stageId <= stageIds.get(stageIds.size() - 1)) {
                throw new Malformed("payload.allowedStageIds must be strictly ascending");
            }
            stageIds.add((int) stageId);
        }
        return stageIds;
    }

    private static void requireExactFields(JsonNode node, Set<String> expected, String name) {
        if (node == null || !node.isObject()) {
            throw new Malformed(name + " must be a JSON object");
        }
        Set<String> actual = new TreeSet<>();
        node.properties().forEach(entry -> actual.add(entry.getKey()));
        if (!actual.equals(new TreeSet<>(expected))) {
            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(actual);
            Set<String> unexpected = new TreeSet<>(actual);
            unexpected.removeAll(expected);
            throw new Malformed(name + " fields differ from v1: missing " + missing + ", unexpected " + unexpected);
        }
    }

    private static long safeInteger(JsonNode node, String field) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new Malformed(field + " must be an integer");
        }
        long value = node.asLong();
        if (value < 0 || value > MAX_SAFE_INTEGER) {
            throw new Malformed(field + " must be in 0.." + MAX_SAFE_INTEGER);
        }
        return value;
    }

    private static String text(JsonNode node, String field, Pattern pattern) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || !pattern.matcher(value.asString()).matches()) {
            throw new Malformed(field + " must be lowercase 0x hex of the required length");
        }
        return value.asString();
    }

    private static String address(JsonNode node, String field) {
        String value = text(node, field, ADDRESS);
        if (ZERO_ADDRESS.equals(value)) {
            throw new Malformed(field + " must not be the zero address");
        }
        return value;
    }

    private String jcsHash(JsonNode payloadNode) {
        try {
            byte[] canonical = new JsonCanonicalizer(mapper.writeValueAsBytes(payloadNode)).getEncodedUTF8();
            return Numeric.toHexString(keccak(canonical));
        } catch (IOException | RuntimeException exception) {
            throw new Malformed("payload could not be canonicalized (JCS)");
        }
    }

    private static byte[] keccak(byte[] value) {
        return Hash.sha3(value);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Arrays.stream(values).mapToInt(value -> value.length).sum());
        for (byte[] value : values) {
            output.writeBytes(value);
        }
        return output.toByteArray();
    }

    private record Envelope(
            StationCertificatePayload payload, String payloadHash, String signature, String computedPayloadHash) {
    }

    private static final class Malformed extends RuntimeException {

        Malformed(String message) {
            super(message, null, false, false);
        }
    }
}
