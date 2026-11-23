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
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import pl.pz.supplyblockchain.application.ports.ChainUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class Web3jConfirmedBlockTimeTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String HEAD_WITH_TIMESTAMP =
            "{\"timestamp\":\"0x6b49d200\"}";

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
    void readsTimestampOfTheLatestBlock() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":" + HEAD_WITH_TIMESTAMP + "}";

        long timestamp = new Web3jConfirmedBlockTime(web3j).confirmedTimestamp();

        assertThat(timestamp).isEqualTo(1_800_000_000L);
        assertThat(requests).hasSize(1);
        JsonNode request = requests.getFirst();
        assertThat(request.get("method").asString()).isEqualTo("eth_getBlockByNumber");
        assertThat(request.at("/params/0").asString()).isEqualTo("latest");
        assertThat(request.at("/params/1").asBoolean()).isFalse();
    }

    @Test
    void rpcErrorIsNotTreatedAsAConfirmedHead() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,"
                + "\"error\":{\"code\":-32000,\"message\":\"node is down\"}}";

        assertThatThrownBy(() -> new Web3jConfirmedBlockTime(web3j).confirmedTimestamp())
                .isInstanceOf(ChainUnavailableException.class);
    }

    @Test
    void missingTimestampIsNotTreatedAsAConfirmedHead() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":{\"timestamp\":null}}";

        assertThatThrownBy(() -> new Web3jConfirmedBlockTime(web3j).confirmedTimestamp())
                .isInstanceOf(ChainUnavailableException.class);
    }

    @Test
    void missingBlockIsNotTreatedAsAConfirmedHead() {
        responseBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":null}";

        assertThatThrownBy(() -> new Web3jConfirmedBlockTime(web3j).confirmedTimestamp())
                .isInstanceOf(ChainUnavailableException.class);
    }
}
