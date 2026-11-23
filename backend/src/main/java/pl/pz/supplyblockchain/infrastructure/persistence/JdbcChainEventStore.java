package pl.pz.supplyblockchain.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import pl.pz.supplyblockchain.application.ports.BatchProjection;
import pl.pz.supplyblockchain.application.ports.BatchProjectionRepository;
import pl.pz.supplyblockchain.application.ports.BlockAnchor;
import pl.pz.supplyblockchain.application.ports.ChainEvent;
import pl.pz.supplyblockchain.application.ports.ChainEventStore;
import pl.pz.supplyblockchain.application.ports.IndexerCheckpoint;
import pl.pz.supplyblockchain.config.ChainIndexerProperties;
import pl.pz.supplyblockchain.domain.DeploymentId;

@Repository
public class JdbcChainEventStore implements ChainEventStore, BatchProjectionRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcChainEventStore.class);
    private static final String STREAM = "chain-events";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ChainIndexerProperties properties;
    private final Clock clock;

    public JdbcChainEventStore(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ChainIndexerProperties properties,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IndexerCheckpoint> findCheckpoint(DeploymentId deploymentId) {
        List<IndexerCheckpoint> rows = jdbcTemplate.query(
                """
                SELECT scanned_block_number, scanned_block_hash, updated_at
                  FROM indexer_checkpoints
                 WHERE chain_id = ? AND contract_address = ? AND stream = ?
                """,
                (rs, rowNum) -> new IndexerCheckpoint(
                        rs.getLong("scanned_block_number"),
                        rs.getString("scanned_block_hash"),
                        rs.getTimestamp("updated_at").toInstant()),
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                STREAM);
        return rows.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockAnchor> findRecentAnchors(DeploymentId deploymentId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT block_number, block_hash
                  FROM indexer_block_anchors
                 WHERE chain_id = ? AND contract_address = ?
                 ORDER BY block_number DESC
                 LIMIT ?
                """,
                (rs, rowNum) -> new BlockAnchor(rs.getLong("block_number"), rs.getString("block_hash")),
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                limit);
    }

    @Override
    @Transactional
    public void rewind(DeploymentId deploymentId, long blockNumber, String blockHash) {
        Instant now = clock.instant();
        jdbcTemplate.update(
                """
                WITH reverted AS (
                    UPDATE chain_events
                       SET canonical = FALSE
                     WHERE chain_id = ? AND contract_address = ?
                       AND canonical AND block_number > ?
                    RETURNING command_id
                )
                UPDATE commands c
                   SET status = 'FAILED',
                       outcome = NULL,
                       failure_code = 'CHAIN_REORG',
                       retry_allowed_after = NULL,
                       retry_at = NULL,
                       lease_owner = NULL,
                       lease_until = NULL,
                       updated_at = ?,
                       version = version + 1
                 WHERE c.id IN (SELECT command_id FROM reverted WHERE command_id IS NOT NULL)
                   AND c.status = 'CONFIRMED'
                   AND NOT EXISTS (
                       SELECT 1 FROM chain_events e
                        WHERE e.command_id = c.id
                          AND e.chain_id = ? AND e.contract_address = ?
                          AND e.canonical AND e.block_number <= ?)
                """,
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                blockNumber,
                Timestamp.from(now),
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                blockNumber);

        jdbcTemplate.update(
                "DELETE FROM batch_projections WHERE chain_id = ? AND contract_address = ?",
                deploymentId.chainId(),
                deploymentId.contractAddress());
        jdbcTemplate.update(
                "DELETE FROM stage_projections WHERE chain_id = ? AND contract_address = ?",
                deploymentId.chainId(),
                deploymentId.contractAddress());
        List<String> payloads = jdbcTemplate.query(
                """
                SELECT payload::text
                  FROM chain_events
                 WHERE chain_id = ? AND contract_address = ? AND canonical
                 ORDER BY block_number, transaction_index, log_index
                """,
                (rs, rowNum) -> rs.getString(1),
                deploymentId.chainId(),
                deploymentId.contractAddress());
        for (String payload : payloads) {
            try {
                updateProjection(deploymentId, objectMapper.readValue(payload, ChainEvent.class));
            } catch (JacksonException exception) {
                throw new IllegalStateException("stored chain event payload is invalid", exception);
            }
        }

        jdbcTemplate.update(
                """
                DELETE FROM indexer_block_anchors
                 WHERE chain_id = ? AND contract_address = ? AND block_number > ?
                """,
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                blockNumber);
        jdbcTemplate.update(
                """
                INSERT INTO indexer_checkpoints
                    (chain_id, contract_address, stream, scanned_block_number, scanned_block_hash, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (chain_id, contract_address, stream) DO UPDATE
                    SET scanned_block_number = EXCLUDED.scanned_block_number,
                        scanned_block_hash = EXCLUDED.scanned_block_hash,
                        updated_at = EXCLUDED.updated_at
                """,
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                STREAM,
                blockNumber,
                blockHash,
                Timestamp.from(now));
        jdbcTemplate.update(
                """
                INSERT INTO indexer_block_anchors (chain_id, contract_address, block_number, block_hash)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (chain_id, contract_address, block_number) DO UPDATE
                    SET block_hash = EXCLUDED.block_hash
                """,
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                blockNumber,
                blockHash);
    }

    @Override
    @Transactional
    public void applyRange(
            DeploymentId deploymentId,
            List<ChainEvent> events,
            long scannedBlockNumber,
            String scannedBlockHash,
            List<BlockAnchor> anchors) {
        for (ChainEvent event : events) {
            UUID commandId = correlateCommand(deploymentId, event);
            jdbcTemplate.update(
                    """
                    INSERT INTO chain_events (
                        chain_id, contract_address, block_hash, log_index, block_number,
                        transaction_index, transaction_hash, event_type, batch_id, command_id,
                        payload, canonical)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), TRUE)
                    ON CONFLICT (chain_id, contract_address, block_hash, log_index) DO UPDATE
                        SET event_type = EXCLUDED.event_type,
                            batch_id = EXCLUDED.batch_id,
                            command_id = EXCLUDED.command_id,
                            payload = EXCLUDED.payload,
                            canonical = TRUE
                    """,
                    deploymentId.chainId(),
                    deploymentId.contractAddress(),
                    event.blockHash(),
                    event.logIndex(),
                    event.blockNumber(),
                    event.transactionIndex(),
                    event.transactionHash().toLowerCase(Locale.ROOT),
                    event.eventType(),
                    event.batchId(),
                    commandId,
                    event.payloadJson());
            updateCommandStatus(commandId, event);
            updateProjection(deploymentId, event);
        }

        Instant now = clock.instant();
        jdbcTemplate.update(
                """
                INSERT INTO indexer_checkpoints
                    (chain_id, contract_address, stream, scanned_block_number, scanned_block_hash, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (chain_id, contract_address, stream) DO UPDATE
                    SET scanned_block_number = EXCLUDED.scanned_block_number,
                        scanned_block_hash = EXCLUDED.scanned_block_hash,
                        updated_at = EXCLUDED.updated_at
                """,
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                STREAM,
                scannedBlockNumber,
                scannedBlockHash,
                Timestamp.from(now));
        for (BlockAnchor anchor : anchors) {
            jdbcTemplate.update(
                    """
                    INSERT INTO indexer_block_anchors (chain_id, contract_address, block_number, block_hash)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (chain_id, contract_address, block_number) DO UPDATE
                        SET block_hash = EXCLUDED.block_hash
                    """,
                    deploymentId.chainId(),
                    deploymentId.contractAddress(),
                    anchor.blockNumber(),
                    anchor.blockHash());
        }
        jdbcTemplate.update(
                """
                DELETE FROM indexer_block_anchors
                 WHERE chain_id = ? AND contract_address = ?
                   AND block_number < ?
                """,
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                Math.max(0, scannedBlockNumber - properties.reorgWindowBlocks() + 1));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BatchProjection> findByBatchId(DeploymentId deploymentId, String batchId) {
        return findProjection(
                deploymentId,
                "p.batch_id = ?",
                batchId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BatchProjection> findByBatchNumber(DeploymentId deploymentId, String batchNumber) {
        return findProjection(
                deploymentId,
                "p.batch_number = ?",
                batchNumber);
    }

    private Optional<BatchProjection> findProjection(
            DeploymentId deploymentId, String predicate, String value) {
        List<BatchProjection> rows = jdbcTemplate.query(
                """
                SELECT p.batch_id, p.batch_number, p.next_stage, p.completed,
                       COALESCE(c.scanned_block_number, 0) AS last_indexed_block,
                       c.updated_at
                  FROM batch_projections p
                  LEFT JOIN indexer_checkpoints c
                    ON c.chain_id = p.chain_id
                   AND c.contract_address = p.contract_address
                   AND c.stream = ?
                 WHERE p.chain_id = ? AND p.contract_address = ? AND %s
                """.formatted(predicate),
                (rs, rowNum) -> mapProjection(rs),
                STREAM,
                deploymentId.chainId(),
                deploymentId.contractAddress(),
                value);
        return rows.stream().findFirst();
    }

    private BatchProjection mapProjection(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        boolean stale = updatedAt == null
                || updatedAt.toInstant().isBefore(clock.instant().minusSeconds(properties.staleAfterSeconds()));
        return new BatchProjection(
                rs.getString("batch_id"),
                rs.getString("batch_number"),
                rs.getInt("next_stage"),
                rs.getBoolean("completed"),
                rs.getLong("last_indexed_block"),
                stale);
    }

    private UUID correlateCommand(DeploymentId deploymentId, ChainEvent event) {
        List<CommandCandidate> candidates = jdbcTemplate.query(
                """
                SELECT c.id, c.type, c.batch_id, c.batch_number, c.stage_id,
                       c.station_address, c.report_hash, c.cid
                  FROM transaction_attempts ta
                  JOIN commands c ON c.id = ta.command_id
                 WHERE lower(ta.tx_hash) = ?
                   AND c.chain_id = ? AND c.contract_address = ?
                """,
                (rs, rowNum) -> new CommandCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getString("batch_id"),
                        rs.getString("batch_number"),
                        (Integer) rs.getObject("stage_id"),
                        rs.getString("station_address"),
                        rs.getString("report_hash"),
                        rs.getString("cid")),
                event.transactionHash().toLowerCase(Locale.ROOT),
                deploymentId.chainId(),
                deploymentId.contractAddress());
        for (CommandCandidate candidate : candidates) {
            if (matches(candidate, event)) {
                return candidate.id();
            }
        }
        if (!candidates.isEmpty()) {
            LOG.warn("chain event {} did not match command payload for tx {}", event.eventType(), event.transactionHash());
        }
        return null;
    }

    private static boolean matches(CommandCandidate command, ChainEvent event) {
        if ("BatchCreated".equals(event.eventType())) {
            return "CREATE_BATCH".equals(command.type())
                    && equalHex(command.batchId(), event.batchId())
                    && command.batchNumber() != null
                    && command.batchNumber().equals(event.batchNumber());
        }
        if (!"STAGE".equals(command.type())
                || !equalHex(command.batchId(), event.batchId())
                || command.stageId() == null
                || !command.stageId().equals(event.stageId())
                || !equalAddress(command.stationAddress(), event.stationAddress())
                || !equalHex(command.reportHash(), event.reportHash())) {
            return false;
        }
        return command.cid() == null || command.cid().equals(event.cid());
    }

    private void updateCommandStatus(UUID commandId, ChainEvent event) {
        if (commandId == null) {
            return;
        }
        String outcome = "StageRejected".equals(event.eventType()) ? "REJECTED" : "ACCEPTED";
        jdbcTemplate.update(
                """
                UPDATE commands
                   SET status = 'CONFIRMED',
                       outcome = ?,
                       failure_code = NULL,
                       retry_allowed_after = NULL,
                       retry_at = NULL,
                       lease_owner = NULL,
                       lease_until = NULL,
                       updated_at = ?,
                       version = version + 1
                 WHERE id = ?
                """,
                outcome,
                Timestamp.from(clock.instant()),
                commandId);
    }

    private void updateProjection(DeploymentId deploymentId, ChainEvent event) {
        if ("BatchCreated".equals(event.eventType())) {
            jdbcTemplate.update(
                    """
                    INSERT INTO batch_projections (
                        chain_id, contract_address, batch_id, batch_number,
                        next_stage, completed, last_event_block, last_event_hash)
                    VALUES (?, ?, ?, ?, 0, FALSE, ?, ?)
                    ON CONFLICT (chain_id, contract_address, batch_id) DO UPDATE
                        SET batch_number = EXCLUDED.batch_number,
                            last_event_block = EXCLUDED.last_event_block,
                            last_event_hash = EXCLUDED.last_event_hash
                    """,
                    deploymentId.chainId(),
                    deploymentId.contractAddress(),
                    event.batchId(),
                    event.batchNumber(),
                    event.blockNumber(),
                    event.blockHash());
            return;
        }
        String outcome = switch (event.eventType()) {
            case "StageCompleted" -> "COMPLETED";
            case "StageRejected" -> "REJECTED";
            default -> null;
        };
        if (outcome != null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO stage_projections (
                        chain_id, contract_address, batch_id, stage_id, report_hash, outcome,
                        station_address, qa_operator, cid, transaction_hash, log_index,
                        block_number, block_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (chain_id, contract_address, batch_id, stage_id, report_hash)
                    DO UPDATE SET outcome = EXCLUDED.outcome,
                                  station_address = EXCLUDED.station_address,
                                  qa_operator = EXCLUDED.qa_operator,
                                  cid = EXCLUDED.cid,
                                  transaction_hash = EXCLUDED.transaction_hash,
                                  log_index = EXCLUDED.log_index,
                                  block_number = EXCLUDED.block_number,
                                  block_hash = EXCLUDED.block_hash
                    """,
                    deploymentId.chainId(),
                    deploymentId.contractAddress(),
                    event.batchId(),
                    event.stageId(),
                    event.reportHash(),
                    outcome,
                    event.stationAddress(),
                    event.qaOperator(),
                    event.cid(),
                    event.transactionHash(),
                    event.logIndex(),
                    event.blockNumber(),
                    event.blockHash());
        }
        if ("StageCompleted".equals(event.eventType())) {
            int nextStage = event.stageId() + 1;
            jdbcTemplate.update(
                    """
                    UPDATE batch_projections
                       SET next_stage = GREATEST(next_stage, ?),
                           completed = GREATEST(next_stage, ?) >= 4,
                           last_event_block = ?,
                           last_event_hash = ?
                     WHERE chain_id = ? AND contract_address = ? AND batch_id = ?
                    """,
                    nextStage,
                    nextStage,
                    event.blockNumber(),
                    event.blockHash(),
                    deploymentId.chainId(),
                    deploymentId.contractAddress(),
                    event.batchId());
        } else if ("StageRejected".equals(event.eventType())) {
            jdbcTemplate.update(
                    """
                    UPDATE batch_projections
                       SET last_event_block = ?, last_event_hash = ?
                     WHERE chain_id = ? AND contract_address = ? AND batch_id = ?
                    """,
                    event.blockNumber(),
                    event.blockHash(),
                    deploymentId.chainId(),
                    deploymentId.contractAddress(),
                    event.batchId());
        }
    }

    private static boolean equalHex(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static boolean equalAddress(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private record CommandCandidate(
            UUID id,
            String type,
            String batchId,
            String batchNumber,
            Integer stageId,
            String stationAddress,
            String reportHash,
            String cid) {
    }
}
