package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import pl.pz.supplyblockchain.domain.StationCertificate;

@Entity
@Table(name = "station_certificates")
class StationCertificateEntity {

    @Id
    @Column(name = "certificate_id", length = 66)
    private String certificateId;

    @Column(name = "station_address", nullable = false, length = 42)
    private String stationAddress;

    @Column(name = "certificate_json", nullable = false)
    private String certificateJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StationCertificateEntity() {
    }

    static StationCertificateEntity from(StationCertificate certificate) {
        StationCertificateEntity entity = new StationCertificateEntity();
        entity.certificateId = certificate.certificateId();
        entity.stationAddress = certificate.stationAddress();
        entity.certificateJson = certificate.certificateJson();
        entity.createdAt = certificate.createdAt();
        return entity;
    }

    StationCertificate toDomain() {
        return new StationCertificate(certificateId, stationAddress, certificateJson, createdAt);
    }
}
