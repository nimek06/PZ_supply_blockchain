package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.StationIdentityVerification;

/**
 * Verifies a station certificate (DEC-13) against the issuer allowlist, the validity window and the
 * on-chain station registry. Rejections are returned as a {@link StationIdentityVerification} reason,
 * never thrown. Run it again on report receipt and before the worker sends: ACTIVE is the result of the
 * current check, not a stored flag.
 *
 * <p>Both methods throw {@link StationRegistryException} when the registry cannot be read; that is an
 * availability error ({@code DEPENDENCY_UNAVAILABLE}), not a verdict about the station. They are
 * read-only and may be retried.
 *
 * <p>{@code certificateJson} is the certificate envelope (certificateVersion, payload, payloadHash,
 * signature). Any faithful JSON serialization is accepted: the payload hash is computed over its JCS form.
 */
public interface StationCertificateVerifier {

    /** Certificate-only check, e.g. when the certificate is registered or shown to an operator. */
    StationIdentityVerification verify(String certificateJson);

    /**
     * Full check for a stage report: the certificate must be ACTIVE, {@code reportSignerAddress}
     * (recovered from the StageCompletion signature) must equal {@code stationAddress}, and
     * {@code stageId} must be in {@code allowedStageIds}.
     */
    StationIdentityVerification verifyForReport(String certificateJson, String reportSignerAddress, int stageId);
}
