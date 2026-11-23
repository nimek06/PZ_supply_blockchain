package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;
import org.web3j.abi.datatypes.generated.Bytes32;
import tools.jackson.databind.ObjectMapper;
import pl.pz.supplyblockchain.config.ChainIndexerProperties;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.infrastructure.persistence.JdbcChainEventStore;

class ChainEventIndexerTest {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String BATCH_ID =
            "0xbd23cd342e8d6764ba34c70b9da72967ec708e76f1acf807f196c4557b5e635b";
    private static final String REPORT_HASH = "0x" + "44".repeat(32);
    private static final String STATION = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String QA = "0x3c44cdddb6a900fa2b585dd299e03d12fa4293bc";

    private static final Event BATCH_CREATED = new Event(
            "BatchCreated",
            List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Utf8String>() {}));
    private static final Event STAGE_COMPLETED = new Event(
            "StageCompleted",
            List.of(
                    new TypeReference<Bytes32>(true) {},
                    new TypeReference<Uint32>() {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Address>() {},
                    new TypeReference<Bytes32>() {},
                    new TypeReference<Utf8String>() {}));
    private static final Event STAGE_REJECTED = new Event(
            "StageRejected",
            List.of(
                    new TypeReference<Bytes32>(true) {},
                    new TypeReference<Uint32>() {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Bytes32>() {},
                    new TypeReference<Utf8String>() {}));

    private final ChainEventIndexer indexer = new ChainEventIndexer(
            mock(Web3j.class),
            new ChainProperties(31337, CONTRACT, "SupplyChain", "1", 3600),
            new ChainIndexerProperties(false, 1, 2000, 10000, 64, 10),
            mock(JdbcChainEventStore.class),
            new ObjectMapper());

    @Test
    void decodesBatchCreatedFields() throws Exception {
        Log log = log(
                BATCH_CREATED,
                List.of(bytes32(BATCH_ID)),
                List.of(new Utf8String("B-2026-0001")));

        var event = indexer.decode(log);

        assertThat(event.eventType()).isEqualTo("BatchCreated");
        assertThat(event.batchId()).isEqualTo(BATCH_ID);
        assertThat(event.batchNumber()).isEqualTo("B-2026-0001");
    }

    @Test
    void decodesStageCompletedAndStageRejectedFields() throws Exception {
        Log completed = log(
                STAGE_COMPLETED,
                List.of(bytes32(BATCH_ID), new Address(STATION)),
                List.of(new Uint32(0), new Address(QA), bytes32(REPORT_HASH), new Utf8String("bafy-stage")));
        Log rejected = log(
                STAGE_REJECTED,
                List.of(bytes32(BATCH_ID), new Address(STATION)),
                List.of(new Uint32(1), bytes32(REPORT_HASH), new Utf8String("bafy-rejected")));

        var completedEvent = indexer.decode(completed);
        var rejectedEvent = indexer.decode(rejected);

        assertThat(completedEvent.stageId()).isZero();
        assertThat(completedEvent.stationAddress()).isEqualTo(STATION);
        assertThat(completedEvent.qaOperator()).isEqualTo(QA);
        assertThat(completedEvent.reportHash()).isEqualTo(REPORT_HASH);
        assertThat(completedEvent.cid()).isEqualTo("bafy-stage");
        assertThat(rejectedEvent.eventType()).isEqualTo("StageRejected");
        assertThat(rejectedEvent.stageId()).isEqualTo(1);
        assertThat(rejectedEvent.cid()).isEqualTo("bafy-rejected");
    }

    @Test
    void rejectsUnknownEventTopics() {
        Log log = log(null, List.of(), List.of());

        assertThatThrownBy(() -> indexer.decode(log))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("unknown event topic");
    }

    @Test
    void refusesToRunOutsideTheLocalAnvilChain() {
        assertThatThrownBy(() -> new ChainEventIndexer(
                        mock(Web3j.class),
                        new ChainProperties(80002, CONTRACT, "SupplyChain", "1", 3600),
                        new ChainIndexerProperties(false, 1, 2000, 10000, 64, 10),
                        mock(JdbcChainEventStore.class),
                        new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only supports chainId 31337");
    }

    private static Log log(Event event, List<Type> indexed, List<Type> data) {
        Log log = new Log();
        log.setAddress(CONTRACT);
        log.setBlockNumber("0x1");
        log.setBlockHash("0x" + "11".repeat(32));
        log.setTransactionHash("0x" + "22".repeat(32));
        log.setTransactionIndex("0x0");
        log.setLogIndex("0x0");
        log.setData(FunctionEncoder.encodeConstructor(data));
        List<String> topics = new ArrayList<>();
        topics.add(event == null ? "0x" + "00".repeat(32) : EventEncoder.encode(event));
        for (Type value : indexed) {
            topics.add(FunctionEncoder.encodeConstructor(List.of(value)));
        }
        log.setTopics(topics);
        return log;
    }

    private static Bytes32 bytes32(String value) {
        return new Bytes32(Numeric.hexStringToByteArray(value));
    }
}
