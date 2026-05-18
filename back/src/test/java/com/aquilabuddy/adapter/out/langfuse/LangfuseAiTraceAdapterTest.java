package com.aquilabuddy.adapter.out.langfuse;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class LangfuseAiTraceAdapterTest {

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
    void sendsLangfuseCompatibleGenerationEvent() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(207)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"successes\":1,\"errors\":[]}"));
        LangfuseAiTraceAdapter adapter = adapter();

        adapter.record(AiCallObservation.success(
            "vertex-ai",
            AiCallOperation.COMPLETION,
            "gemini-2.5-flash",
            "ai-client.complete.v1",
            Duration.ofMillis(42),
            12,
            5,
            Instant.parse("2026-05-18T07:00:00Z")
        ));

        RecordedRequest request = server.takeRequest();
        String expectedAuth = "Basic " + Base64.getEncoder()
            .encodeToString("public-key:secret-key".getBytes(StandardCharsets.UTF_8));
        String body = request.getBody().readUtf8();
        assertThat(request.getPath()).isEqualTo("/api/public/ingestion");
        assertThat(request.getHeader("Authorization")).isEqualTo(expectedAuth);
        assertThat(body).contains("\"type\":\"generation-create\"");
        assertThat(body).contains("\"provider\":\"vertex-ai\"");
        assertThat(body).contains("\"operation\":\"COMPLETION\"");
        assertThat(body).contains("\"promptVersion\":\"ai-client.complete.v1\"");
        assertThat(body).contains("\"model\":\"gemini-2.5-flash\"");
        assertThat(body).contains("\"latencyMillis\":42");
        assertThat(body).contains("\"input\":12");
        assertThat(body).contains("\"output\":5");
        assertThat(body).contains("\"environment\":\"test\"");
    }

    @Test
    void sendsFailureReasonAndLatencyMetadata() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(207)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"successes\":1,\"errors\":[]}"));
        LangfuseAiTraceAdapter adapter = adapter();

        adapter.record(AiCallObservation.failure(
            "openai-compatible",
            AiCallOperation.EMBEDDING,
            "text-embedding-3-small",
            "ai-client.embed.v1",
            Duration.ofMillis(13),
            "SocketTimeoutException",
            "provider request timed out",
            Instant.parse("2026-05-18T07:00:00Z")
        ));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"level\":\"ERROR\"");
        assertThat(body).contains("\"statusMessage\":\"SocketTimeoutException\"");
        assertThat(body).contains("\"provider\":\"openai-compatible\"");
        assertThat(body).contains("\"operation\":\"EMBEDDING\"");
        assertThat(body).contains("\"promptVersion\":\"ai-client.embed.v1\"");
        assertThat(body).contains("\"model\":\"text-embedding-3-small\"");
        assertThat(body).contains("\"latencyMillis\":13");
        assertThat(body).contains("\"errorType\":\"SocketTimeoutException\"");
        assertThat(body).contains("\"failureReason\":\"provider request timed out\"");
    }

    @Test
    void swallowsLangfuseTransportFailure() {
        server.enqueue(new MockResponse().setResponseCode(500));
        LangfuseAiTraceAdapter adapter = adapter();

        adapter.record(AiCallObservation.failure(
            "vertex-ai",
            AiCallOperation.EMBEDDING,
            "gemini-embedding-001",
            "ai-client.embed.v1",
            Duration.ofMillis(7),
            "IllegalStateException",
            Instant.parse("2026-05-18T07:00:00Z")
        ));

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    private LangfuseAiTraceAdapter adapter() {
        return new LangfuseAiTraceAdapter(
            RestClient.builder(),
            new LangfuseProperties(
                true,
                server.url("/").toString(),
                "public-key",
                "secret-key",
                "test"
            )
        );
    }
}
