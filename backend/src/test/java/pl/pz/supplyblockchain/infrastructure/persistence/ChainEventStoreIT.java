package pl.pz.supplyblockchain.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pl.pz.supplyblockchain.application.ports.ChainEvent;
import pl.pz.supplyblockchain.application.ports.ChainEventStore;
import pl.pz.supplyblockchain.application.ports.BatchProjectionRepository;
import pl.pz.supplyblockchain.domain.DeploymentId;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class ChainEventStoreIT {

    private static final long CHAIN_ID = 31337;
    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String OWNER = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String BATCH_ID = "0x" + "11".repeat(32);
    private static final String REPORT_HASH = "0x" + "22".repeat(32);
    private static final String COMPLETED_REPORT_HASH = "0x" + "23".repeat(32);
    private static final String BLOCK_HASH_1 = "0x" + "a1".repeat(32);
    private static final String BLOCK_HASH_2 = "0x" + "a2".repeat(32);
    private static final String BLOCK_HASH_3 = "0x" + "a3".repeat(32);
    private static final String TX_HASH_CREATE = "0x" + "c1".repeat(32);
    private static final String TX_HASH_REJECT = "0x" + "c2".repeat(32);
    private static final String TX_HASH_COMPLETE = "0x" + "c3".repeat(32);
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final DeploymentId DEPLOYMENT = new DeploymentId(CHAIN_ID, CONTRACT);

    @Autowired
    private ChainEventStore eventStore;

    @Autowired
    private BatchProjectionRepository projectionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM chain_events");
        jdbc.update("DELETE FROM indexer_block_anchors");
        jdbc.update("DELETE FROM indexer_checkpoints");
        jdbc.update("DELETE FROM batch_projections");
        jdbc.update("DELETE FROM transaction_attempts");
        jdbc.update("DELETE FROM commands");
    }

    @Test
    void appliesAndDeduplicatesEventsThenRewindsStatusesAndProjectionAtomically() throws Exception {
        UUID createCommand = insertCommand("CREATE_BATCH", null, null, null, BATCH_ID, "B-2026-0001");
        UUID rejectCommand = insertCommand("STAGE", 0, STATION, REPORT_HASH, BATCH_ID, null);
        jdbc.update(
                "UPDATE commands SET status = 'CONFIRMED', outcome = 'REJECTED' WHERE id = ?",
                rejectCommand);
        UUID completeCommand = insertCommand("STAGE", 0, STATION, COMPLETED_REPORT_HASH, BATCH_ID, null);
        insertAttempt(createCommand, TX_HASH_CREATE, 0);
        insertAttempt(rejectCommand, TX_HASH_REJECT, 1);
        insertAttempt(completeCommand, TX_HASH_COMPLETE, 2);

        ChainEvent created = event("BatchCreated", 1, BLOCK_HASH_1, 0, TX_HASH_CREATE,
                "B-2026-0001", null, null, null, null, null);
        ChainEvent rejected = event("StageRejected", 2, BLOCK_HASH_2, 1, TX_HASH_REJECT,
                null, 0, STATION, REPORT_HASH, "bafy-rejected", null);
        ChainEvent completed = event("StageCompleted", 3, BLOCK_HASH_3, 2, TX_HASH_COMPLETE,
                null, 0, STATION, COMPLETED_REPORT_HASH, "bafy-complete", OWNER);

        apply(List.of(created, rejected, completed), 3, BLOCK_HASH_3);
        apply(List.of(created, rejected, completed), 3, BLOCK_HASH_3);

        var projection = projectionRepository.findByBatchId(DEPLOYMENT, BATCH_ID).orElseThrow();
        assertThat(projection.nextStage()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chain_events WHERE canonical", Integer.class))
                .isEqualTo(3);
        assertThat(commandStatus(createCommand)).isEqualTo("CONFIRMED");
        assertThat(commandOutcome(createCommand)).isEqualTo("ACCEPTED");
        assertThat(commandStatus(rejectCommand)).isEqualTo("CONFIRMED");
        assertThat(commandOutcome(rejectCommand)).isEqualTo("REJECTED");
        assertThat(commandOutcome(completeCommand)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stage_projections", Integer.class))
                .isEqualTo(2);

        eventStore.rewind(DEPLOYMENT, 1, BLOCK_HASH_1);

        var rewound = projectionRepository.findByBatchId(DEPLOYMENT, BATCH_ID).orElseThrow();
        assertThat(rewound.nextStage()).isZero();
        assertThat(commandStatus(createCommand)).isEqualTo("CONFIRMED");
        assertThat(commandStatus(rejectCommand)).isEqualTo("FAILED");
        assertThat(commandFailureCode(rejectCommand)).isEqualTo("CHAIN_REORG");
        assertThat(commandStatus(completeCommand)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM chain_events WHERE canonical", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stage_projections", Integer.class))
                .isZero();
    }

    private void apply(List<ChainEvent> events, long block, String hash) {
        eventStore.applyRange(DEPLOYMENT, events, block, hash, List.of());
    }

    private UUID insertCommand(
            String type,
            Integer stageId,
            String station,
            String reportHash,
            String batchId,
            String batchNumber) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO commands (
                    id, chain_id, contract_address, type, owner_address, batch_id, batch_number,
                    stage_id, station_address, station_certificate_id, report_hash,
                    station_signature, signed_payload_digest, deadline, qa_passed,
                    status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, 1800000000, FALSE,
                        'SUBMITTED', ?, ?)
                """,
                id,
                CHAIN_ID,
                CONTRACT,
                type,
                OWNER,
                batchId,
                batchNumber,
                stageId,
                station,
                stageId == null ? null : "cert-1",
                reportHash,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT));
        return id;
    }

    private void insertAttempt(UUID commandId, String txHash, long nonce) {
        jdbc.update(
                """
                INSERT INTO transaction_attempts (
                    id, command_id, relayer_address, tx_nonce, tx_hash, signed_tx,
                    calldata_hash, max_priority_fee_per_gas, max_fee_per_gas, gas_limit, created_at)
                VALUES (?, ?, ?, ?, ?, '0x01', ?, 1, 2, 21000, ?)
                """,
                UUID.randomUUID(),
                commandId,
                OWNER,
                nonce,
                txHash,
                "0x" + "33".repeat(32),
                Timestamp.from(CREATED_AT));
    }

    private ChainEvent event(
            String type,
            long block,
            String blockHash,
            long logIndex,
            String txHash,
            String batchNumber,
            Integer stage,
            String station,
            String reportHash,
            String cid,
            String qaOperator) throws Exception {
        ChainEvent event = new ChainEvent(
                type,
                block,
                blockHash,
                0,
                logIndex,
                txHash,
                BATCH_ID,
                batchNumber,
                stage,
                station,
                qaOperator,
                reportHash,
                cid,
                "{}");
        return new ChainEvent(
                event.eventType(),
                event.blockNumber(),
                event.blockHash(),
                event.transactionIndex(),
                event.logIndex(),
                event.transactionHash(),
                event.batchId(),
                event.batchNumber(),
                event.stageId(),
                event.stationAddress(),
                event.qaOperator(),
                event.reportHash(),
                event.cid(),
                objectMapper.writeValueAsString(event));
    }

    private String commandStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM commands WHERE id = ?", String.class, id);
    }

    private String commandOutcome(UUID id) {
        return jdbc.queryForObject("SELECT outcome FROM commands WHERE id = ?", String.class, id);
    }

    private String commandFailureCode(UUID id) {
        return jdbc.queryForObject("SELECT failure_code FROM commands WHERE id = ?", String.class, id);
    }
}
