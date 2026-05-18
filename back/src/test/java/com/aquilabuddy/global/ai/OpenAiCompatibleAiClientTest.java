package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleAiClientTest {

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
    void sendsChatCompletionRequestAndReturnsFirstChoiceContent() {
        route("/chat/completions", 200, """
            {
              "choices": [
                {
                  "message": {
                    "content": "TITLE: docs: update guide\\nBODY: ## Summary\\n- Update the guide."
                  }
                }
              ]
            }
            """);
        OpenAiCompatibleAiClient client = client("test-key");

        String content = client.complete("You write concise PR drafts.", "Create a PR draft.");

        assertThat(content).isEqualTo("TITLE: docs: update guide\nBODY: ## Summary\n- Update the guide.");
        assertThat(capturedAuthorization).isEqualTo("Bearer test-key");
        assertThat(capturedBody).contains("\"model\":\"gpt-4o-mini\"");
        assertThat(capturedBody).contains("\"temperature\":0.2");
        assertThat(capturedBody).contains("\"role\":\"system\"");
        assertThat(capturedBody).contains("\"content\":\"You write concise PR drafts.\"");
        assertThat(capturedBody).contains("\"role\":\"user\"");
        assertThat(capturedBody).contains("\"content\":\"Create a PR draft.\"");
    }

    @Test
    void rejectsBlankApiKeyBeforeCallingProvider() {
        OpenAiCompatibleAiClient client = client(" ");

        assertThatThrownBy(() -> client.complete("system", "user"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.ai.openai-compatible.api-key");
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
                    0.2
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
