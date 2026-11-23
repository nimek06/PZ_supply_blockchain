package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import pl.pz.supplyblockchain.application.ports.ChainReceipt;
import pl.pz.supplyblockchain.application.ports.ChainUnavailableException;
import pl.pz.supplyblockchain.application.ports.RelayerTransactionException;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.TransactionReceiptStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class Web3jRelayerChainGatewayTest {

    private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
    private static final String TX_HASH = "0x" + "d".repeat(64);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String GET_BATCH_RESULT = "0x"
            + "0".repeat(63) + "1"
            + "0".repeat(63) + "0"
            + "0".repeat(64);

    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private volatile String responseBody;
    private HttpServer server;
    private Web3j web3j;

    @BeforeEach
    void startStubRpc() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                JsonNode request = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
                requests.add(request);
                byte[] body = responseBody.replace("$ID", request.get("id").toString())
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        web3j = Web3j.build(new HttpService("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stopStubRpc() {
        web3j.shutdown();
        server.stop(0);
    }

    @Test
    void readsBatchAtLatestFromTheConfiguredContract() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":\"" + GET_BATCH_RESULT + "\"}";

        var state = gateway().getBatch("0x" + "a".repeat(64));

        assertThat(state.exists()).isTrue();
        assertThat(state.nextStage()).isZero();
        assertThat(state.completed()).isFalse();
        JsonNode request = requests.getFirst();
        assertThat(request.get("method").asString()).isEqualTo("eth_call");
        assertThat(request.at("/params/0/to").asString()).isEqualTo(CONTRACT);
        assertThat(request.at("/params/0/data").asString())
                .startsWith(Hash.sha3String("getBatch(bytes32)").substring(0, 10));
        assertThat(request.at("/params/1").asString()).isEqualTo("latest");
    }

    @Test
    void invalidBatchResultIsNotTreatedAsSuccess() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":\"0x1234\"}";

        assertThatThrownBy(() -> gateway().getBatch("0x" + "a".repeat(64)))
                .isInstanceOf(ChainUnavailableException.class);
    }

    @Test
    void readsSuccessfulAndRevertedReceipts() {
        responseBody = receiptResponse("0x1");
        ChainReceipt success = gateway().findReceipt(TX_HASH).orElseThrow();
        assertThat(success.status()).isEqualTo(TransactionReceiptStatus.SUCCESS);
        assertThat(success.blockNumber()).isEqualTo(9L);
        assertThat(success.blockHash()).isEqualTo("0x" + "9".repeat(64));

        responseBody = receiptResponse("0x0");
        ChainReceipt reverted = gateway().findReceipt(TX_HASH).orElseThrow();
        assertThat(reverted.status()).isEqualTo(TransactionReceiptStatus.REVERTED);
    }

    @Test
    void missingReceiptIsEmpty() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":null}";

        assertThat(gateway().findReceipt(TX_HASH)).isEmpty();
    }

    @Test
    void sendsRawTransactionAndReturnsItsHash() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":\"" + TX_HASH + "\"}";

        assertThat(gateway().sendRawTransaction("0xraw")).isEqualTo(TX_HASH);
        assertThat(requests.getFirst().get("method").asString()).isEqualTo("eth_sendRawTransaction");
        assertThat(requests.getFirst().at("/params/0").asString()).isEqualTo("0xraw");
    }

    @Test
    void explicitPermanentSendErrorIsNotRetriedAsSuccess() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,"
                + "\"error\":{\"code\":-32000,\"message\":\"insufficient funds for gas * price + value\"}}";

        assertThatThrownBy(() -> gateway().sendRawTransaction("0xraw"))
                .isInstanceOf(RelayerTransactionException.class)
                .satisfies(exception -> {
                    assertThat(((RelayerTransactionException) exception).retryable()).isFalse();
                    assertThat(((RelayerTransactionException) exception).failureCode())
                            .isEqualTo("SEND_REJECTED");
                });
    }

    @Test
    void estimateRevertIsPermanent() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,"
                + "\"error\":{\"code\":3,\"message\":\"execution reverted: UnexpectedStage\"}}";

        assertThatThrownBy(() -> gateway().estimateGas("0xfrom", CONTRACT, "0xdata"))
                .isInstanceOf(RelayerTransactionException.class)
                .satisfies(exception -> assertThat(
                        ((RelayerTransactionException) exception).retryable()).isFalse());
    }

    private Web3jRelayerChainGateway gateway() {
        return new Web3jRelayerChainGateway(
                web3j, new ChainProperties(31337, CONTRACT, "SupplyChain", "1", 3600));
    }

    private static String receiptResponse(String status) {
        return "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":{"
                + "\"transactionHash\":\"" + TX_HASH + "\","
                + "\"transactionIndex\":\"0x0\","
                + "\"blockHash\":\"0x" + "9".repeat(64) + "\","
                + "\"blockNumber\":\"0x9\","
                + "\"from\":\"0x" + "1".repeat(40) + "\","
                + "\"to\":\"" + CONTRACT + "\","
                + "\"cumulativeGasUsed\":\"0x1\","
                + "\"gasUsed\":\"0x1\","
                + "\"contractAddress\":null,"
                + "\"logs\":[],"
                + "\"logsBloom\":\"0x" + "0".repeat(512) + "\","
                + "\"status\":\"" + status + "\","
                + "\"effectiveGasPrice\":\"0x1\"}}";
    }
}
