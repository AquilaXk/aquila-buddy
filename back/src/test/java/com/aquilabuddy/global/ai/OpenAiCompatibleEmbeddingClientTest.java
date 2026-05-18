package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.ai.AiCallOperation;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleEmbeddingClientTest {

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
    void sendsEmbeddingRequestAndReturnsFirstVector() throws Exception {
        enqueueJson(200, """
            {
              "data": [
                {
                  "embedding": [0.1, 0.2, -0.3],
                  "index": 0
                }
              ],
              "model": "text-embedding-3-small",
              "usage": {
                "prompt_tokens": 14,
                "total_tokens": 14
              }
            }
            """);
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        OpenAiCompatibleAiClient client = client("test-key", observer);

        EmbeddingVector vector = client.embed("Java Spring open source contributor");

        RecordedRequest request = server.takeRequest();
        assertThat(vector.values()).containsExactly(0.1, 0.2, -0.3);
        assertThat(request.getPath()).isEqualTo("/embeddings");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"text-embedding-3-small\"");
        assertThat(body).contains("\"input\":\"Java Spring open source contributor\"");
        assertThat(body).contains("\"encoding_format\":\"float\"");
        assertThat(body).contains("\"dimensions\":3");
        assertThat(observer.firstObservation().provider()).isEqualTo("openai-compatible");
        assertThat(observer.firstObservation().operation()).isEqualTo(AiCallOperation.EMBEDDING);
        assertThat(observer.firstObservation().model()).isEqualTo("text-embedding-3-small");
        assertThat(observer.firstObservation().promptVersion()).isEqualTo("ai-client.embed.v1");
        assertThat(observer.firstObservation().success()).isTrue();
        assertThat(observer.firstObservation().inputTokens()).isEqualTo(14);
        assertThat(observer.firstObservation().outputTokens()).isNull();
    }

    @Test
    void wrapsEmbeddingHttpFailureWithStatus() {
        enqueueJson(429, """
            {
              "error": {
                "message": "rate limit"
              }
            }
            """);
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        OpenAiCompatibleAiClient client = client("test-key", observer);

        assertThatThrownBy(() -> client.embed("Java Spring"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI embedding request failed")
            .hasMessageContaining("429");
        assertThat(observer.firstObservation().success()).isFalse();
        assertThat(observer.firstObservation().operation()).isEqualTo(AiCallOperation.EMBEDDING);
        assertThat(observer.firstObservation().errorType()).isEqualTo("RestClientResponseException");
        assertThat(observer.firstObservation().failureReason())
            .contains("429")
            .contains("rate limit");
    }

    @Test
    void rejectsEmbeddingResponseWithoutData() {
        enqueueJson(200, """
            {
              "data": []
            }
            """);
        OpenAiCompatibleAiClient client = client("test-key");

        assertThatThrownBy(() -> client.embed("Java Spring"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("data");
    }

    @Test
    void rejectsEmbeddingResponseWithoutVector() {
        enqueueJson(200, """
            {
              "data": [
                {
                  "embedding": null,
                  "index": 0
                }
              ]
            }
            """);
        OpenAiCompatibleAiClient client = client("test-key");

        assertThatThrownBy(() -> client.embed("Java Spring"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("embedding vector");
    }

    @Test
    void rejectsEmbeddingResponseWhenDimensionDoesNotMatchRequest() {
        enqueueJson(200, """
            {
              "data": [
                {
                  "embedding": [0.1, 0.2],
                  "index": 0
                }
              ],
              "model": "text-embedding-3-small",
              "usage": {
                "prompt_tokens": 8,
                "total_tokens": 8
              }
            }
            """);
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        OpenAiCompatibleAiClient client = client("test-key", observer);

        assertThatThrownBy(() -> client.embed("Java Spring"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dimension");
        assertThat(observer.firstObservation().success()).isFalse();
        assertThat(observer.firstObservation().operation()).isEqualTo(AiCallOperation.EMBEDDING);
        assertThat(observer.firstObservation().errorType()).isEqualTo("IllegalStateException");
        assertThat(observer.firstObservation().failureReason())
            .contains("dimension")
            .contains("3")
            .contains("2");
    }

    @Test
    void rejectsBlankEmbeddingInputBeforeCallingProvider() {
        OpenAiCompatibleAiClient client = client("test-key");

        assertThatThrownBy(() -> client.embed(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("input");
    }

    private OpenAiCompatibleAiClient client(String apiKey) {
        return client(apiKey, new RecordingAiCallObserver());
    }

    private OpenAiCompatibleAiClient client(String apiKey, RecordingAiCallObserver observer) {
        return new OpenAiCompatibleAiClient(
            RestClient.builder(),
            new OpenAiCompatibleProperties(
                server.url("/").toString(),
                apiKey,
                "gpt-4o-mini",
                0.2,
                "text-embedding-3-small",
                3,
                30_000
            ),
            observer
        );
    }

    private void enqueueJson(int statusCode, String body) {
        server.enqueue(new MockResponse()
            .setResponseCode(statusCode)
            .addHeader("Content-Type", "application/json")
            .setBody(body));
    }
}
