package pl.pz.supplyblockchain.infrastructure.persistence;

import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.pz.supplyblockchain.application.ports.BatchSequenceRepository;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;

@Repository
public class JdbcBatchSequenceRepository implements BatchSequenceRepository {

    private final JdbcTemplate jdbc;

    public JdbcBatchSequenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int nextValue(DeploymentId deploymentId, int year) {
        Integer allocated = jdbc.queryForObject("""
                INSERT INTO batch_sequences (chain_id, contract_address, year, next_value)
                VALUES (?, ?, ?, 2)
                ON CONFLICT (chain_id, contract_address, year)
                DO UPDATE SET next_value = batch_sequences.next_value + 1
                RETURNING next_value - 1
                """, Integer.class, deploymentId.chainId(), deploymentId.contractAddress(), year);
        if (allocated == null) {
            throw new DomainException(
                    ErrorCode.DEPENDENCY_UNAVAILABLE, "batch number sequence is unavailable");
        }
        return Objects.requireNonNull(allocated);
    }
}
