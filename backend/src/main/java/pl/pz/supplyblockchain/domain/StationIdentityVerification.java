package pl.pz.supplyblockchain.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Outcome of verifying a station certificate, optionally bound to a signed report.
 * {@link #status()} describes the station identity (states from {@code docs/station-identity.md});
 * {@link #accepted()} says whether this particular request may proceed. A report signed by another
 * address is rejected even though the station itself is {@link Status#ACTIVE}.
 *
 * @param payload     parsed payload, {@code null} when the certificate format is invalid
 * @param payloadHash keccak256 of the JCS payload computed by the backend, {@code null} if not reached
 * @param digest      EIP-712 digest signed by the issuer, {@code null} if not reached
 */
public record StationIdentityVerification(
        Reason reason,
        String detail,
        StationCertificatePayload payload,
        String payloadHash,
        String digest) {

    public enum Status {
        ACTIVE,
        PENDING_ACTIVATION,
        EXPIRED,
        REVOKED,
        REGISTRY_MISMATCH,
        /** The certificate itself is not acceptable (format, hash, signature, issuer, deployment, time). */
        INVALID
    }

    /** Checks run in this order; the first failing one is reported. */
    public enum Reason {
        OK(Status.ACTIVE),
        /** Not a v1 certificate: invalid JSON, extra/missing fields, wrong types or values. */
        MALFORMED(Status.INVALID),
        PAYLOAD_HASH_MISMATCH(Status.INVALID),
        /** Signature is not canonical or does not recover to {@code issuerAddress}. */
        INVALID_ISSUER_SIGNATURE(Status.INVALID),
        /** Signature is valid, but the issuer is not on the deployment allowlist. */
        UNTRUSTED_ISSUER(Status.INVALID),
        /** {@code chainId} or {@code verifyingContract} differs from the configured deployment. */
        WRONG_DEPLOYMENT(Status.INVALID),
        NOT_YET_VALID(Status.INVALID),
        EXPIRED(Status.EXPIRED),
        /** Valid certificate, but the station address was never registered on-chain. */
        NOT_REGISTERED(Status.PENDING_ACTIVATION),
        REVOKED(Status.REVOKED),
        /** Registered and active on-chain, but stationId, expiresAt or stageMask differ. */
        REGISTRY_MISMATCH(Status.REGISTRY_MISMATCH),
        /** Station is active, but the report was signed by a different address. */
        SIGNER_MISMATCH(Status.ACTIVE),
        /** Station is active, but the stage is outside {@code allowedStageIds}. */
        STAGE_NOT_ALLOWED(Status.ACTIVE);

        private final Status status;

        Reason(Status status) {
            this.status = status;
        }

        public Status status() {
            return status;
        }

        /** Stable snake_case code, the same as {@code expectedVerification.reason} in shared fixtures. */
        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public StationIdentityVerification {
        Objects.requireNonNull(reason, "reason");
    }

    public boolean accepted() {
        return reason == Reason.OK;
    }

    public Status status() {
        return reason.status();
    }

    public String code() {
        return reason.code();
    }
}
