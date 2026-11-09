package pl.pz.supplyblockchain.domain;

import java.util.List;

/**
 * Signed part of a station certificate (DEC-13, {@code shared/schemas/station-certificate.schema.json}).
 * Addresses and hashes are lowercase {@code 0x} hex; times are Unix seconds, {@code expiresAt} exclusive.
 * Instances are produced by the certificate verifier after format validation; the hash is always
 * computed from the received JSON, never from this record.
 */
public record StationCertificatePayload(
        int schemaVersion,
        String certificateId,
        String stationId,
        String stationAddress,
        String issuerAddress,
        long chainId,
        String verifyingContract,
        long issuedAt,
        long expiresAt,
        List<Integer> allowedStageIds) {

    public StationCertificatePayload {
        allowedStageIds = List.copyOf(allowedStageIds);
    }

    /** Bit mask in the on-chain {@code registerStation} format: bit {@code i} set for stage {@code i}. */
    public int stageMask() {
        int mask = 0;
        for (int stageId : allowedStageIds) {
            mask |= 1 << stageId;
        }
        return mask;
    }

    public boolean allowsStage(int stageId) {
        return allowedStageIds.contains(stageId);
    }
}
