package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.domain.ai.AiCallOperation;
import java.lang.reflect.Method;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
              ],
              "usage": {
                "prompt_tokens": 120,
                "completion_tokens": 32
              }
            }
            """);
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        OpenAiCompatibleAiClient client = client("test-key", observer);

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
        assertThat(observer.firstObservation().provider()).isEqualTo("openai-compatible");
        assertThat(observer.firstObservation().operation()).isEqualTo(AiCallOperation.COMPLETION);
        assertThat(observer.firstObservation().model()).isEqualTo("gpt-4o-mini");
        assertThat(observer.firstObservation().promptVersion()).isEqualTo("ai-client.complete.v1");
        assertThat(observer.firstObservation().success()).isTrue();
        assertThat(observer.firstObservation().inputTokens()).isEqualTo(120);
        assertThat(observer.firstObservation().outputTokens()).isEqualTo(32);
        assertThat(observer.firstObservation().latencyMillis()).isGreaterThanOrEqualTo(0);
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
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        OpenAiCompatibleAiClient client = client("test-key", observer);

        assertThatThrownBy(() -> client.complete("system", "user"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI completion request failed")
            .hasMessageContaining("502");
        assertThat(observer.firstObservation().success()).isFalse();
        assertThat(observer.firstObservation().operation()).isEqualTo(AiCallOperation.COMPLETION);
        assertThat(observer.firstObservation().errorType()).isEqualTo("RestClientResponseException");
        assertThat(observer.firstObservation().failureReason())
            .contains("502")
            .contains("upstream unavailable");
    }

    @Test
    void recordsTimeoutFailureWhenChatProviderDoesNotRespondInTime() {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setHeadersDelay(2, TimeUnit.SECONDS)
            .setBody("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "slow response"
                      }
                    }
                  ]
                }
                """));
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        OpenAiCompatibleAiClient client = client("test-key", observer, 100);

        assertThatThrownBy(() -> client.complete("system", "user"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI completion request failed");
        assertThat(observer.firstObservation().success()).isFalse();
        assertThat(observer.firstObservation().operation()).isEqualTo(AiCallOperation.COMPLETION);
        assertThat(observer.firstObservation().failureReason()).contains("timeout");
    }

    @Test
    void normalizesTimeoutFailureFromNestedCauseMessages() throws Exception {
        Method failureReason = OpenAiCompatibleAiClient.class
            .getDeclaredMethod("failureReason", RuntimeException.class);
        failureReason.setAccessible(true);

        String reason = (String) failureReason.invoke(
            null,
            new RestClientException("I/O error", new HttpTimeoutException("request timed out"))
        );

        assertThat(reason).contains("timeout");
    }

    @Test
    void normalizesTimeoutFailureFromNestedCauseType() throws Exception {
        Method failureReason = OpenAiCompatibleAiClient.class
            .getDeclaredMethod("failureReason", RuntimeException.class);
        failureReason.setAccessible(true);

        String reason = (String) failureReason.invoke(
            null,
            new RestClientException("I/O error", new TimeoutWithoutMessageException())
        );

        assertThat(reason).contains("timeout");
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
        return client(apiKey, new RecordingAiCallObserver());
    }

    private OpenAiCompatibleAiClient client(String apiKey, RecordingAiCallObserver observer) {
        return client(apiKey, observer, 30_000);
    }

    private OpenAiCompatibleAiClient client(String apiKey, RecordingAiCallObserver observer, int requestTimeoutMillis) {
        return new OpenAiCompatibleAiClient(
            RestClient.builder(),
            new OpenAiCompatibleProperties(
                server.url("/").toString(),
                apiKey,
                "gpt-4o-mini",
                0.2,
                "text-embedding-3-small",
                1536,
                requestTimeoutMillis
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

    private static class TimeoutWithoutMessageException extends RuntimeException {
    }
}
