package pl.pz.supplyblockchain.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pz.supplyblockchain.application.ports.StationCertificateRepository;
import pl.pz.supplyblockchain.domain.StationCertificate;

@Repository
public class JpaStationCertificateRepository implements StationCertificateRepository {

    private final StationCertificateJpaRepository repository;

    public JpaStationCertificateRepository(StationCertificateJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StationCertificate> findById(String certificateId) {
        return repository.findById(certificateId).map(StationCertificateEntity::toDomain);
    }

    @Override
    @Transactional
    public StationCertificate save(StationCertificate certificate) {
        return repository.save(StationCertificateEntity.from(certificate)).toDomain();
    }
}
