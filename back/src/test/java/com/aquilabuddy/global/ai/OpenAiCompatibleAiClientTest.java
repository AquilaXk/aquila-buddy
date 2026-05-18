package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleAiClientTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
    }

    @Test
    void sendsChatCompletionRequestAndReturnsFirstChoiceContent() throws Exception {
        enqueueJson(200, """
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

        RecordedRequest request = server.takeRequest();
        assertThat(content).isEqualTo("TITLE: docs: update guide\nBODY: ## Summary\n- Update the guide.");
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"gpt-4o-mini\"");
        assertThat(body).contains("\"temperature\":0.2");
        assertThat(body).contains("\"role\":\"system\"");
        assertThat(body).contains("\"content\":\"You write concise PR drafts.\"");
        assertThat(body).contains("\"role\":\"user\"");
        assertThat(body).contains("\"content\":\"Create a PR draft.\"");
    }

    @Test
    void wrapsChatCompletionHttpFailureWithStatus() {
        enqueueJson(502, """
            {
              "error": {
                "message": "upstream unavailable"
              }
            }
            """);
        OpenAiCompatibleAiClient client = client("test-key");

        assertThatThrownBy(() -> client.complete("system", "user"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI completion request failed")
            .hasMessageContaining("502");
    }

    @Test
    void rejectsChatCompletionResponseWithoutChoices() {
        enqueueJson(200, """
            {
              "choices": []
            }
            """);
        OpenAiCompatibleAiClient client = client("test-key");

        assertThatThrownBy(() -> client.complete("system", "user"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("choices");
    }

    @Test
    void rejectsChatCompletionResponseWithBlankContent() {
        enqueueJson(200, """
            {
              "choices": [
                {
                  "message": {
                    "content": " "
                  }
                }
              ]
            }
            """);
        OpenAiCompatibleAiClient client = client("test-key");

        assertThatThrownBy(() -> client.complete("system", "user"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI completion content");
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
                    server.url("/").toString(),
                    apiKey,
                    "gpt-4o-mini",
                    0.2
                )
            )
        );
    }

    private void enqueueJson(int statusCode, String body) {
        server.enqueue(new MockResponse()
            .setResponseCode(statusCode)
            .addHeader("Content-Type", "application/json")
            .setBody(body));
    }
}
