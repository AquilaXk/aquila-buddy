package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.TranslatePullRequestDraftPort;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftAnalysisContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftIssueContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryGuideContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * openai-compatible 운영 모드에서 주요 AI adapter들이 fixture 없이 provider HTTP 경로를 타는지 검증합니다.
 */
@SpringBootTest
class AiOperationalModeIntegrationTest {

    private static final MockWebServer SERVER = startServer();

    @Autowired
    private GenerateIssueGuidePort generateIssueGuidePort;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private GeneratePullRequestDraftPort generatePullRequestDraftPort;

    @Autowired
    private TranslatePullRequestDraftPort translatePullRequestDraftPort;

    @Autowired
    private Environment environment;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private OpenAiCompatibleProperties openAiCompatibleProperties;

    @DynamicPropertySource
    static void aiProviderProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ai.provider", () -> "openai-compatible");
        registry.add("app.ai.openai-compatible.api-base-url", () -> SERVER.url("/").toString());
        registry.add("app.ai.openai-compatible.api-key", () -> "test-key");
        registry.add("app.ai.openai-compatible.chat-model", () -> "ops-chat-model");
        registry.add("app.ai.openai-compatible.embedding-model", () -> "ops-embedding-model");
        registry.add("app.ai.openai-compatible.embedding-dimensions", () -> "3");
    }

    @AfterAll
    static void stopServer() throws IOException {
        SERVER.shutdown();
    }

    @Test
    void issueGuideUsesOpenAiCompatibleChatProvider() throws Exception {
        assertThat(environment.getProperty("app.ai.openai-compatible.api-key")).isEqualTo("test-key");
        assertThat(aiProperties.providerOrDefault()).isEqualTo("openai-compatible");
        assertThat(openAiCompatibleProperties.apiKeyOrBlank()).isEqualTo("test-key");
        enqueueChat("""
            GUIDELINE: Keep the change focused around validation.
            PSEUDO_CODE:
            1. Add a regression test
            SIDE_EFFECTS:
            - API wording can change
            """);

        IssueGuide guide = generateIssueGuidePort.generateIssueGuide(issue());

        RecordedRequest request = takeRequest();
        assertThat(guide.guideline()).contains("validation");
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"ops-chat-model\"");
        assertThat(body).contains("Issue number: #2");
        assertThat(body).contains("Refine validation error example");
    }

    @Test
    void pullRequestDraftUsesOpenAiCompatibleChatProvider() throws Exception {
        enqueueChat("""
            TITLE: docs: improve validation guide
            BODY: ## Summary
            - Improve the validation guide.
            """);

        PullRequestDraft draft = generatePullRequestDraftPort.generate(prDraftRequest());

        RecordedRequest request = takeRequest();
        assertThat(draft.title()).isEqualTo("docs: improve validation guide");
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"ops-chat-model\"");
        assertThat(body).contains("Diff:");
        assertThat(body).contains("Repository guide context");
        assertThat(body).contains("Issue context");
        assertThat(body).contains("Analysis context");
    }

    @Test
    void pullRequestDraftTranslationUsesOpenAiCompatibleChatProvider() throws Exception {
        enqueueChat("""
            TITLE: docs: prepare validation guide update
            BODY: ## Summary
            - Translate the source draft.
            """);

        PullRequestDraft draft = translatePullRequestDraftPort.translate(storedDraft(), "en");

        RecordedRequest request = takeRequest();
        assertThat(draft.title()).isEqualTo("docs: prepare validation guide update");
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"ops-chat-model\"");
        assertThat(body).contains("Target language: en");
        assertThat(body).contains("Source title: [Docs] README 기여 안내 보완");
        assertThat(body).contains("Source body:");
    }

    @Test
    void embeddingUsesOpenAiCompatibleEmbeddingProvider() throws Exception {
        enqueueJson("""
            {
              "data": [
                {
                  "embedding": [0.1, 0.2, -0.3],
                  "index": 0
                }
              ],
              "model": "ops-embedding-model"
            }
            """);

        EmbeddingVector vector = embeddingClient.embed("Java Spring open source contributor");

        RecordedRequest request = takeRequest();
        assertThat(vector.values()).containsExactly(0.1, 0.2, -0.3);
        assertThat(request.getPath()).isEqualTo("/embeddings");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"ops-embedding-model\"");
        assertThat(body).contains("\"input\":\"Java Spring open source contributor\"");
        assertThat(body).contains("\"dimensions\":3");
    }

    private static RecommendedIssue issue() {
        return new RecommendedIssue(
            "demo-2",
            "spring-projects/spring-boot",
            2L,
            "Refine validation error example",
            "Java/Spring validation 예시 개선 이슈입니다.",
            0.87,
            List.of("good first issue", "validation"),
            "OPEN",
            "현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다."
        );
    }

    private static PullRequestDraftRequest prDraftRequest() {
        return new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            new PullRequestDraftRepositoryGuideContext(
                "Run tests before PR.",
                "## Summary\n- Describe changes",
                List.of("## Summary\n- Improve docs")
            ),
            new PullRequestDraftIssueContext(
                "Fix validation message",
                "Error message should explain invalid input.",
                "Matches Java validation topic"
            ),
            new PullRequestDraftAnalysisContext(
                "Follow validation guide.",
                "if invalid then return helpful message",
                List.of("Update documentation examples.")
            )
        );
    }

    private static StoredPullRequestDraft storedDraft() {
        return new StoredPullRequestDraft(
            "draft-1",
            "spring-projects/spring-boot",
            123,
            "main",
            "feat/readme-fix",
            "[Docs] README 기여 안내 보완",
            "## 변경 사항\n- README 안내를 보완합니다.",
            Instant.parse("2026-05-15T01:00:00Z")
        );
    }

    private static void enqueueChat(String content) {
        enqueueJson("""
            {
              "choices": [
                {
                  "message": {
                    "content": "%s"
                  }
                }
              ]
            }
            """.formatted(content.replace("\n", "\\n")));
    }

    private static void enqueueJson(String body) {
        SERVER.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(body));
    }

    private static RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = SERVER.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        return request;
    }

    private static MockWebServer startServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
