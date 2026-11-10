package pl.pz.supplyblockchain.application.ports;

import java.util.Optional;
import pl.pz.supplyblockchain.domain.StationCertificate;

public interface StationCertificateRepository {

    Optional<StationCertificate> findById(String certificateId);

    StationCertificate save(StationCertificate certificate);
}
