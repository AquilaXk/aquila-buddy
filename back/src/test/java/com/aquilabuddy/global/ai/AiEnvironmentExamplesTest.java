package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.Map.entry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AiEnvironmentExamplesTest {

    @Test
    void envExamplesUseVertexAiAndDoNotExposeLegacyApiKeyNames() throws IOException {
        String rootExample = Files.readString(Path.of("../.env.example"));
        String backendExample = Files.readString(Path.of(".env.example"));

        assertThat(rootExample)
            .contains("AI_PROVIDER=vertex-ai")
            .contains("GOOGLE_CLOUD_PROJECT_ID=grepp-ai-project")
            .contains("VERTEX_AI_LOCATION=us-central1")
            .contains("VERTEX_AI_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_CREDENTIALS_PATH=./vertex-key.json")
            .contains("LANGFUSE_ENABLED=false")
            .contains("LANGFUSE_BASE_URL=https://cloud.langfuse.com")
            .contains("LANGFUSE_PUBLIC_KEY=")
            .contains("LANGFUSE_SECRET_KEY=")
            .doesNotContain(legacyGlmApiKeyName())
            .doesNotContain("GEMINI_API_KEY");
        assertThat(backendExample)
            .contains("AI_PROVIDER=vertex-ai")
            .contains("GOOGLE_CLOUD_PROJECT_ID=grepp-ai-project")
            .contains("VERTEX_AI_LOCATION=us-central1")
            .contains("VERTEX_AI_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_CREDENTIALS_PATH=./vertex-key.json")
            .contains("LANGFUSE_ENABLED=false")
            .contains("LANGFUSE_BASE_URL=https://cloud.langfuse.com")
            .contains("LANGFUSE_PUBLIC_KEY=")
            .contains("LANGFUSE_SECRET_KEY=")
            .doesNotContain(legacyGlmApiKeyName())
            .doesNotContain("GEMINI_API_KEY");
    }

    @Test
    void runtimeDocsAndDevConfigurationKeepVertexAiDefaultAndRuntimeSmokeAllowsSupportedProviders()
        throws IOException {
        String applicationDev = Files.readString(Path.of("src/main/resources/application-dev.yml"));
        String readme = Files.readString(Path.of("../README.md"));
        String smokeScript = Files.readString(Path.of("../tools/smoke/validate-ai-runtime-env.sh"));

        assertThat(applicationDev)
            .contains("provider: ${AI_PROVIDER:vertex-ai}")
            .doesNotContain(legacyGlmApiKeyName());
        assertThat(readme)
            .contains("기본 운영 AI provider는 `vertex-ai`입니다.")
            .contains("`AI_PROVIDER=openai-compatible`: OpenAI-compatible chat completion/embedding provider를 사용합니다.")
            .contains("AI_API_BASE_URL=https://api.openai.com/v1")
            .contains("AI_CHAT_MODEL=gpt-4o-mini")
            .contains("AI_EMBEDDING_MODEL=text-embedding-3-small")
            .contains("AI_PROVIDER=openai-compatible \\")
            .contains("LANGFUSE_ENABLED=true")
            .contains("Langfuse tracing은 선택 기능")
            .doesNotContain(legacyGlmApiKeyName());
        assertThat(smokeScript)
            .contains("provider=\"${AI_PROVIDER:-vertex-ai}\"")
            .contains("AI_PROVIDER must be vertex-ai or openai-compatible for runtime smoke.")
            .doesNotContain(legacyGlmApiKeyName());
    }

    @Test
    void operationalSmokeRunbookDocumentsAiGithubAndPgvectorReadiness() throws IOException {
        String readme = Files.readString(Path.of("../README.md"));
        String runbook = Files.readString(Path.of("../tools/smoke/AI_RUNTIME_SMOKE.md"));

        assertThat(readme)
            .contains("[AI 운영 smoke runbook](tools/smoke/AI_RUNTIME_SMOKE.md)")
            .contains("GITHUB_TOKEN_PERMISSIONS=metadata:read,contents:read,pull-requests:read,issues:read");
        assertThat(runbook)
            .contains("# AI 운영 smoke runbook")
            .contains("AI_PROVIDER=openai-compatible")
            .contains("AI_EMBEDDING_DIMENSIONS=1536")
            .contains("GITHUB_TOKEN_PERMISSIONS=metadata:read,contents:read,pull-requests:read,issues:read")
            .contains("Metadata")
            .contains("Contents")
            .contains("Pull requests")
            .contains("Issues")
            .contains("X-Accepted-GitHub-Permissions")
            .contains("RECOMMENDATION_SIMILARITY_PROVIDER=pgvector")
            .contains("schema-postgres-pgvector.sql")
            .contains("LANGFUSE_ENABLED=false")
            .doesNotContain(legacyGlmApiKeyName());
    }

    @Test
    void rootEnvExampleDocumentsOpenAiCompatibleRuntimeSettings() throws IOException {
        String rootExample = Files.readString(Path.of("../.env.example"));

        assertThat(rootExample)
            .contains("GITHUB_TOKEN_PERMISSIONS=metadata:read,contents:read,pull-requests:read,issues:read")
            .contains("# AI_PROVIDER=openai-compatible")
            .contains("AI_API_BASE_URL=https://api.openai.com/v1")
            .contains("AI_API_KEY=")
            .contains("OPENAI_API_KEY=")
            .contains("AI_CHAT_MODEL=gpt-4o-mini")
            .contains("AI_TEMPERATURE=0.2")
            .contains("AI_EMBEDDING_MODEL=text-embedding-3-small")
            .contains("AI_EMBEDDING_DIMENSIONS=1536")
            .doesNotContain(legacyGlmApiKeyName());
    }

    @Test
    void backendEnvExampleDocumentsOpenAiCompatibleRuntimeSettings() throws IOException {
        String backendExample = Files.readString(Path.of(".env.example"));

        assertThat(backendExample)
            .contains("GITHUB_TOKEN_PERMISSIONS=metadata:read,contents:read,pull-requests:read,issues:read")
            .contains("# AI_PROVIDER=openai-compatible")
            .contains("AI_API_BASE_URL=https://api.openai.com/v1")
            .contains("AI_API_KEY=")
            .contains("OPENAI_API_KEY=")
            .contains("AI_CHAT_MODEL=gpt-4o-mini")
            .contains("AI_TEMPERATURE=0.2")
            .contains("AI_EMBEDDING_MODEL=text-embedding-3-small")
            .contains("AI_EMBEDDING_DIMENSIONS=1536")
            .doesNotContain(legacyGlmApiKeyName());
    }

    @Test
    void runtimeSmokeAcceptsOpenAiCompatibleProviderWhenRequiredEnvIsPresent()
        throws IOException, InterruptedException {
        SmokeResult result = runSmoke(Map.of(
            "AI_PROVIDER", "openai-compatible",
            "AI_API_KEY", "test-key",
            "AI_CHAT_MODEL", "gpt-test",
            "AI_EMBEDDING_MODEL", "text-embedding-test",
            "AI_EMBEDDING_DIMENSIONS", "1536",
            "GITHUB_TOKEN", "github-token",
            "GITHUB_TOKEN_PERMISSIONS", "metadata:read,contents:read,pull-requests:read,issues:read"
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("provider=openai-compatible")
            .contains("similarity=score");
    }

    @Test
    void runtimeSmokeRejectsOpenAiCompatibleProviderWhenRequiredEnvIsMissing()
        throws IOException, InterruptedException {
        SmokeResult result = runSmoke(Map.of(
            "AI_PROVIDER", "openai-compatible",
            "GITHUB_TOKEN", "github-token"
        ));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output())
            .contains("missing required env: AI_API_KEY or OPENAI_API_KEY")
            .contains("missing required env: AI_CHAT_MODEL")
            .contains("missing required env: AI_EMBEDDING_MODEL")
            .contains("env must be a positive integer: AI_EMBEDDING_DIMENSIONS")
            .contains("missing required env: GITHUB_TOKEN_PERMISSIONS")
            .contains("AI runtime smoke env validation failed.");
    }

    @Test
    void runtimeSmokeRejectsGithubTokenWhenPermissionDeclarationIsMissing()
        throws IOException, InterruptedException {
        SmokeResult result = runSmoke(Map.of(
            "AI_PROVIDER", "openai-compatible",
            "AI_API_KEY", "test-key",
            "AI_CHAT_MODEL", "gpt-test",
            "AI_EMBEDDING_MODEL", "text-embedding-test",
            "AI_EMBEDDING_DIMENSIONS", "1536",
            "GITHUB_TOKEN", "github-token"
        ));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output())
            .contains("missing required env: GITHUB_TOKEN_PERMISSIONS")
            .contains("AI runtime smoke env validation failed.");
    }

    @Test
    void runtimeSmokeRejectsGithubTokenWhenRequiredPermissionIsMissing()
        throws IOException, InterruptedException {
        SmokeResult result = runSmoke(Map.of(
            "AI_PROVIDER", "openai-compatible",
            "AI_API_KEY", "test-key",
            "AI_CHAT_MODEL", "gpt-test",
            "AI_EMBEDDING_MODEL", "text-embedding-test",
            "AI_EMBEDDING_DIMENSIONS", "1536",
            "GITHUB_TOKEN", "github-token",
            "GITHUB_TOKEN_PERMISSIONS", "metadata:read,contents:read"
        ));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output())
            .contains("missing GitHub token permission: pull-requests:read")
            .contains("missing GitHub token permission: issues:read")
            .contains("AI runtime smoke env validation failed.");
    }

    @Test
    void providerE2eSmokeEnvValidationSkipsWhenDisabled() throws IOException, InterruptedException {
        SmokeResult result = runProviderE2eSmoke(Map.of());

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("Provider E2E smoke is disabled.")
            .contains("PROVIDER_E2E_SMOKE_ENABLED=true");
    }

    @Test
    void providerE2eSmokeEnvValidationAcceptsOpenAiCompatiblePgvectorWhenEnabled()
        throws IOException, InterruptedException {
        SmokeResult result = runProviderE2eSmoke(Map.ofEntries(
            entry("PROVIDER_E2E_SMOKE_ENABLED", "true"),
            entry("BACKEND_BASE_URL", "https://api.example.com"),
            entry("AI_PROVIDER", "openai-compatible"),
            entry("AI_API_KEY", "test-key"),
            entry("AI_CHAT_MODEL", "gpt-test"),
            entry("AI_EMBEDDING_MODEL", "text-embedding-test"),
            entry("AI_EMBEDDING_DIMENSIONS", "1536"),
            entry("GITHUB_TOKEN", "github-token"),
            entry("GITHUB_TOKEN_PERMISSIONS", "metadata:read,contents:read,pull-requests:read,issues:read"),
            entry("RECOMMENDATION_SIMILARITY_PROVIDER", "pgvector"),
            entry("DB_URL", "jdbc:postgresql://localhost:5432/aquila_buddy"),
            entry("DB_USER", "aquila"),
            entry("DB_PASSWORD", "secret"),
            entry("RECOMMENDATION_SIMILARITY_LIMIT", "20"),
            entry("EMBEDDING_BACKFILL_OPERATION_TOKEN", "operation-token"),
            entry("DEFAULT_GITHUB_ID", "aquila"),
            entry("PROVIDER_E2E_REPO_FULL_NAME", "owner/repo"),
            entry("PROVIDER_E2E_ISSUE_NUMBER", "123"),
            entry("PROVIDER_E2E_BASE_BRANCH", "main"),
            entry("PROVIDER_E2E_HEAD_BRANCH", "provider-e2e-smoke")
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("provider=openai-compatible")
            .contains("auth=default-github-id")
            .contains("similarity=pgvector");
    }

    @Test
    void providerE2eSmokeEnvValidationRejectsMissingRuntimeContextWhenEnabled()
        throws IOException, InterruptedException {
        SmokeResult result = runProviderE2eSmoke(Map.of(
            "PROVIDER_E2E_SMOKE_ENABLED", "true",
            "AI_PROVIDER", "openai-compatible",
            "GITHUB_TOKEN", "github-token"
        ));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output())
            .contains("missing required env: BACKEND_BASE_URL")
            .contains("missing required env: AI_API_KEY or OPENAI_API_KEY")
            .contains("missing required env: RECOMMENDATION_SIMILARITY_PROVIDER=pgvector")
            .contains("missing required env: DB_URL")
            .contains("missing required env: EMBEDDING_BACKFILL_OPERATION_TOKEN")
            .contains("missing required env: DEFAULT_GITHUB_ID")
            .contains("missing required env: PROVIDER_E2E_REPO_FULL_NAME")
            .contains("Provider E2E smoke env validation failed.");
    }

    @Test
    void providerE2eSmokeRunnerDryRunDocumentsEndToEndBackendPath()
        throws IOException, InterruptedException {
        SmokeResult result = runProviderE2eRunner(Map.ofEntries(
            entry("PROVIDER_E2E_SMOKE_ENABLED", "true"),
            entry("PROVIDER_E2E_DRY_RUN", "true"),
            entry("BACKEND_BASE_URL", "https://api.example.com"),
            entry("AI_PROVIDER", "openai-compatible"),
            entry("AI_API_KEY", "test-key"),
            entry("AI_CHAT_MODEL", "gpt-test"),
            entry("AI_EMBEDDING_MODEL", "text-embedding-test"),
            entry("AI_EMBEDDING_DIMENSIONS", "1536"),
            entry("GITHUB_TOKEN", "github-token"),
            entry("GITHUB_TOKEN_PERMISSIONS", "metadata:read,contents:read,pull-requests:read,issues:read"),
            entry("RECOMMENDATION_SIMILARITY_PROVIDER", "pgvector"),
            entry("DB_URL", "jdbc:postgresql://localhost:5432/aquila_buddy"),
            entry("DB_USER", "aquila"),
            entry("DB_PASSWORD", "secret"),
            entry("RECOMMENDATION_SIMILARITY_LIMIT", "20"),
            entry("EMBEDDING_BACKFILL_OPERATION_TOKEN", "operation-token"),
            entry("DEFAULT_GITHUB_ID", "aquila"),
            entry("PROVIDER_E2E_REPO_FULL_NAME", "owner/repo"),
            entry("PROVIDER_E2E_ISSUE_NUMBER", "123"),
            entry("PROVIDER_E2E_BASE_BRANCH", "main"),
            entry("PROVIDER_E2E_HEAD_BRANCH", "provider-e2e-smoke")
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("01 GET /api/v1/health")
            .contains("02 POST /api/v1/users/me/vector")
            .contains("03 POST /api/v1/issues/crawl/search")
            .contains("04 GET /api/v1/issues/recommend")
            .contains("05 GET /api/v1/issues/lookup")
            .contains("06 GET /api/v1/issues/{issueId}/guide")
            .contains("07 POST /api/v1/pr")
            .contains("08 POST /api/v1/pr/{draftId}/translate")
            .contains("09 GET /api/v1/ai/observability");
    }

    @Test
    void ciWorkflowRunsOperationalSmokeEnvValidation() throws IOException {
        String workflow = Files.readString(Path.of("../.github/workflows/ci.yml"));

        assertThat(workflow)
            .contains("operations-smoke:")
            .contains("name: Operations Smoke")
            .contains("AI_PROVIDER: openai-compatible")
            .contains("GITHUB_TOKEN_PERMISSIONS: metadata:read,contents:read,pull-requests:read,issues:read")
            .contains("RECOMMENDATION_SIMILARITY_PROVIDER: pgvector")
            .contains("bash tools/smoke/validate-ai-runtime-env.sh");
    }

    private static String legacyGlmApiKeyName() {
        return "G" + "LM_API_KEY";
    }

    private static SmokeResult runSmoke(Map<String, String> env) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "../tools/smoke/validate-ai-runtime-env.sh");
        Map<String, String> processEnv = builder.environment();
        processEnv.clear();
        processEnv.putAll(env);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(exited).isTrue();
        return new SmokeResult(process.exitValue(), output);
    }

    private static SmokeResult runProviderE2eSmoke(Map<String, String> env) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "../tools/smoke/validate-provider-e2e-env.sh");
        Map<String, String> processEnv = builder.environment();
        processEnv.clear();
        processEnv.putAll(env);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(exited).isTrue();
        return new SmokeResult(process.exitValue(), output);
    }

    private static SmokeResult runProviderE2eRunner(Map<String, String> env) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "../tools/smoke/run-provider-e2e-smoke.sh");
        Map<String, String> processEnv = builder.environment();
        processEnv.clear();
        processEnv.putAll(env);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(exited).isTrue();
        return new SmokeResult(process.exitValue(), output);
    }

    private record SmokeResult(int exitCode, String output) {
    }

}
