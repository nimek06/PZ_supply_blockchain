package pl.pz.supplyblockchain.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface StationCertificateJpaRepository extends JpaRepository<StationCertificateEntity, String> {
}
