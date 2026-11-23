package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;
import tools.jackson.databind.ObjectMapper;
import pl.pz.supplyblockchain.application.ports.BlockAnchor;
import pl.pz.supplyblockchain.application.ports.ChainEvent;
import pl.pz.supplyblockchain.application.ports.ChainEventStore;
import pl.pz.supplyblockchain.application.ports.IndexerCheckpoint;
import pl.pz.supplyblockchain.config.ChainIndexerProperties;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.DeploymentId;

public class ChainEventIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(ChainEventIndexer.class);
    private static final int MAX_REORG_ANCHORS = 64;

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
    private static final Map<String, Event> EVENTS = Map.of(
            EventEncoder.encode(BATCH_CREATED), BATCH_CREATED,
            EventEncoder.encode(STAGE_COMPLETED), STAGE_COMPLETED,
            EventEncoder.encode(STAGE_REJECTED), STAGE_REJECTED);

    private final Web3j web3j;
    private final ChainProperties chainProperties;
    private final ChainIndexerProperties indexerProperties;
    private final ChainEventStore eventStore;
    private final ObjectMapper objectMapper;
    private final DeploymentId deploymentId;

    public ChainEventIndexer(
            Web3j web3j,
            ChainProperties chainProperties,
            ChainIndexerProperties indexerProperties,
            ChainEventStore eventStore,
            ObjectMapper objectMapper) {
        this.web3j = web3j;
        this.chainProperties = chainProperties;
        this.indexerProperties = indexerProperties;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        if (chainProperties.chainId() != 31337) {
            throw new IllegalArgumentException("the local chain event indexer only supports chainId 31337");
        }
        this.deploymentId = chainProperties.deploymentId();
    }

    @Scheduled(fixedDelayString = "${supplychain.indexer.poll-interval-ms:2000}")
    public void poll() {
        try {
            pollOnce();
        } catch (IOException | RuntimeException exception) {
            LOG.error("chain event indexer poll failed", exception);
        }
    }

    public void pollOnce() throws IOException {
        verifyChain();
        long head = web3j.ethBlockNumber().send().getBlockNumber().longValueExact();
        Optional<IndexerCheckpoint> checkpoint = eventStore.findCheckpoint(deploymentId);
        long nextBlock = checkpoint.map(value -> value.blockNumber() + 1)
                .orElse(indexerProperties.deploymentBlock());
        if (checkpoint.isPresent()) {
            nextBlock = findReorgStart(checkpoint.get());
        }
        if (head < indexerProperties.deploymentBlock()) {
            return;
        }
        verifyContractCode();

        if (nextBlock > head) {
            IndexerCheckpoint current = checkpoint.orElseThrow();
            String headHash = blockHash(head);
            if (head == current.blockNumber() && !headHash.equalsIgnoreCase(current.blockHash())) {
                throw new IllegalStateException("checkpoint block changed without a common anchor");
            }
            List<BlockAnchor> anchors = readAnchors(head);
            if (!anchors.isEmpty()
                    && !anchors.getLast().blockHash().equalsIgnoreCase(headHash)) {
                throw new IOException("chain changed while refreshing block anchors");
            }
            eventStore.applyRange(deploymentId, List.of(), head, headHash, anchors);
            return;
        }

        while (nextBlock <= head) {
            long endBlock = Math.min(head, nextBlock + indexerProperties.blockRange() - 1L);
            scanRange(nextBlock, endBlock);
            nextBlock = endBlock + 1;
        }
    }

    private void verifyChain() throws IOException {
        BigInteger actualChainId = web3j.ethChainId().send().getChainId();
        if (actualChainId.longValueExact() != chainProperties.chainId()) {
            throw new IllegalStateException(
                    "RPC chainId " + actualChainId + " does not match configured chainId "
                            + chainProperties.chainId());
        }
    }

    private void verifyContractCode() throws IOException {
        String code = web3j.ethGetCode(
                        chainProperties.contractAddress(), DefaultBlockParameter.valueOf("latest"))
                .send()
                .getCode();
        if (code == null || "0x".equalsIgnoreCase(code) || "0x0".equalsIgnoreCase(code)) {
            throw new IllegalStateException(
                    "no contract code at configured address " + chainProperties.contractAddress());
        }
    }

    private long findReorgStart(IndexerCheckpoint checkpoint) throws IOException {
        List<BlockAnchor> anchors = eventStore.findRecentAnchors(deploymentId, MAX_REORG_ANCHORS);
        if (anchors.isEmpty()) {
            anchors = List.of(new BlockAnchor(checkpoint.blockNumber(), checkpoint.blockHash()));
        }

        boolean mismatch = false;
        Long commonBlock = null;
        String commonHash = null;
        for (BlockAnchor anchor : anchors) {
            String currentHash = blockHashOrNull(anchor.blockNumber());
            if (currentHash != null && currentHash.equalsIgnoreCase(anchor.blockHash())) {
                commonBlock = anchor.blockNumber();
                commonHash = currentHash;
                break;
            }
            mismatch = true;
        }
        if (!mismatch) {
            return checkpoint.blockNumber() + 1;
        }

        long ancestor = commonBlock == null ? indexerProperties.deploymentBlock() - 1 : commonBlock;
        String ancestorHash = commonHash == null ? blockHash(ancestor) : commonHash;
        if (commonBlock == null) {
            LOG.error(
                    "no common block anchor within {} blocks for deployment {}; rescanning from block {}",
                    indexerProperties.reorgWindowBlocks(),
                    deploymentId,
                    indexerProperties.deploymentBlock());
        } else {
            LOG.warn("chain reorganization detected for {} at block {}", deploymentId, ancestor);
        }
        eventStore.rewind(deploymentId, ancestor, ancestorHash);
        return ancestor + 1;
    }

    private void scanRange(long fromBlock, long toBlock) throws IOException {
        String hashBeforeLogQueries = blockHash(toBlock);
        List<ChainEvent> events = new ArrayList<>();
        for (Map.Entry<String, Event> entry : EVENTS.entrySet()) {
            EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
                    chainProperties.contractAddress());
            filter.addSingleTopic(entry.getKey());
            var response = web3j.ethGetLogs(filter).send();
            if (response.hasError()) {
                throw new IOException("eth_getLogs failed: " + response.getError().getMessage());
            }
            for (EthLog.LogResult<?> result : response.getLogs()) {
                Object raw = result.get();
                if (!(raw instanceof Log log) || log.isRemoved()) {
                    continue;
                }
                if (!chainProperties.contractAddress().equalsIgnoreCase(log.getAddress())) {
                    continue;
                }
                ChainEvent event = decode(log);
                if (event.blockNumber() < fromBlock || event.blockNumber() > toBlock) {
                    throw new IOException("RPC returned a log outside the requested block range");
                }
                if (!blockHash(event.blockNumber()).equalsIgnoreCase(event.blockHash())) {
                    throw new IOException("log block hash no longer matches canonical chain");
                }
                events.add(event);
            }
        }
        events.sort(Comparator.comparingLong(ChainEvent::blockNumber)
                .thenComparingLong(ChainEvent::transactionIndex)
                .thenComparingLong(ChainEvent::logIndex));

        String endHash = blockHash(toBlock);
        if (!hashBeforeLogQueries.equalsIgnoreCase(endHash)) {
            throw new IOException("chain changed while scanning block range");
        }
        List<BlockAnchor> anchors = readAnchors(toBlock);
        if (!anchors.isEmpty()
                && !anchors.getLast().blockHash().equalsIgnoreCase(endHash)) {
            throw new IOException("chain changed while refreshing block anchors");
        }
        eventStore.applyRange(deploymentId, events, toBlock, endHash, anchors);
    }

    private List<BlockAnchor> readAnchors(long endBlock) throws IOException {
        long first = Math.max(
                indexerProperties.deploymentBlock(),
                endBlock - Math.min(indexerProperties.reorgWindowBlocks(), MAX_REORG_ANCHORS) + 1);
        List<BlockAnchor> anchors = new ArrayList<>();
        for (long block = first; block <= endBlock; block++) {
            anchors.add(new BlockAnchor(block, blockHash(block)));
        }
        return anchors;
    }

    private String blockHash(long blockNumber) throws IOException {
        String hash = blockHashOrNull(blockNumber);
        if (hash == null || hash.isBlank()) {
            throw new IOException("RPC returned no block at height " + blockNumber);
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    private String blockHashOrNull(long blockNumber) throws IOException {
        EthBlock response = web3j.ethGetBlockByNumber(
                        DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), false)
                .send();
        EthBlock.Block block = response.getBlock();
        return block == null ? null : block.getHash();
    }

    ChainEvent decode(Log log) throws IOException {
        Event event = EVENTS.get(log.getTopics().getFirst().toLowerCase(Locale.ROOT));
        if (event == null) {
            throw new IOException("unknown event topic in log " + log.getTransactionHash());
        }
        EventValues values = Contract.staticExtractEventParameters(event, log);
        if (values == null) {
            throw new IOException("could not decode event log " + log.getTransactionHash());
        }
        List<Type> indexed = values.getIndexedValues();
        List<Type> data = values.getNonIndexedValues();
        String eventType = event.getName();
        String batchId = bytes32(indexed.get(0));
        Integer stageId = null;
        String station = null;
        String qaOperator = null;
        String reportHash = null;
        String cid = null;
        String batchNumber = null;
        if ("BatchCreated".equals(eventType)) {
            batchNumber = string(data.get(0));
        } else {
            stageId = ((Uint32) data.get(0)).getValue().intValueExact();
            station = address(indexed.get(1));
            int reportHashIndex;
            int cidIndex;
            if ("StageCompleted".equals(eventType)) {
                qaOperator = address(data.get(1));
                reportHashIndex = 2;
                cidIndex = 3;
            } else {
                reportHashIndex = 1;
                cidIndex = 2;
            }
            reportHash = bytes32(data.get(reportHashIndex));
            cid = string(data.get(cidIndex));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("batchId", batchId);
        payload.put("batchNumber", batchNumber);
        payload.put("stageId", stageId);
        payload.put("stationAddress", station);
        payload.put("qaOperator", qaOperator);
        payload.put("reportHash", reportHash);
        payload.put("cid", cid);
        ChainEvent partial = new ChainEvent(
                eventType,
                log.getBlockNumber().longValueExact(),
                log.getBlockHash().toLowerCase(Locale.ROOT),
                log.getTransactionIndex().longValueExact(),
                log.getLogIndex().longValueExact(),
                log.getTransactionHash().toLowerCase(Locale.ROOT),
                batchId,
                batchNumber,
                stageId,
                station,
                qaOperator,
                reportHash,
                cid,
                objectMapper.writeValueAsString(payload));
        return partial;
    }

    private static String bytes32(Type value) {
        return Numeric.toHexString((byte[]) value.getValue()).toLowerCase(Locale.ROOT);
    }

    private static String address(Type value) {
        return ((Address) value).getValue().toLowerCase(Locale.ROOT);
    }

    private static String string(Type value) {
        return ((Utf8String) value).getValue();
    }
}
