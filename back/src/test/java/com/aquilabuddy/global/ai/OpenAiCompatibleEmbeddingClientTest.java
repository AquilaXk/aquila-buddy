package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleEmbeddingClientTest {

    private HttpServer server;
    private String capturedAuthorization;
    private String capturedBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsEmbeddingRequestAndReturnsFirstVector() {
        route("/embeddings", 200, """
            {
              "data": [
                {
                  "embedding": [0.1, 0.2, -0.3],
                  "index": 0
                }
              ],
              "model": "text-embedding-3-small"
            }
            """);
        OpenAiCompatibleAiClient client = client("test-key");

        EmbeddingVector vector = client.embed("Java Spring open source contributor");

        assertThat(vector.values()).containsExactly(0.1, 0.2, -0.3);
        assertThat(capturedAuthorization).isEqualTo("Bearer test-key");
        assertThat(capturedBody).contains("\"model\":\"text-embedding-3-small\"");
        assertThat(capturedBody).contains("\"input\":\"Java Spring open source contributor\"");
        assertThat(capturedBody).contains("\"encoding_format\":\"float\"");
        assertThat(capturedBody).contains("\"dimensions\":3");
    }

    @Test
    void rejectsBlankEmbeddingInputBeforeCallingProvider() {
        OpenAiCompatibleAiClient client = client("test-key");

        assertThatThrownBy(() -> client.embed(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("input");
    }

    private OpenAiCompatibleAiClient client(String apiKey) {
        return new OpenAiCompatibleAiClient(
            RestClient.builder(),
            new AiProperties(
                "openai-compatible",
                new AiProperties.OpenAiCompatible(
                    "http://localhost:" + server.getAddress().getPort(),
                    apiKey,
                    "gpt-4o-mini",
                    0.2,
                    "text-embedding-3-small",
                    3
                )
            )
        );
    }

    private void route(String path, int statusCode, String body) {
        server.createContext(path, exchange -> {
            capturedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            capturedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, statusCode, body);
        });
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
