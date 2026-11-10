package pl.pz.supplyblockchain.domain;

import java.time.Instant;

public record StationCertificate(
        String certificateId,
        String stationAddress,
        String certificateJson,
        Instant createdAt) {
}
