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
            .contains("VERTEX_AI_MODEL=gemini-2.5-pro")
            .contains("VERTEX_AI_ISSUE_SUMMARY_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_ISSUE_GUIDE_MODEL=gemini-2.5-pro")
            .contains("VERTEX_AI_PR_DRAFT_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_PR_TRANSLATION_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_CREDENTIALS_PATH=./vertex-key.json")
            .contains("LANGFUSE_ENABLED=false")
            .contains("LANGFUSE_BASE_URL=https://cloud.langfuse.com")
            .contains("LANGFUSE_PUBLIC_KEY=")
            .contains("LANGFUSE_SECRET_KEY=")
            .contains("LANGFUSE_SMOKE_ENABLED=false")
            .contains("LANGFUSE_SMOKE_MODE=dry-run")
            .contains("LANGFUSE_SMOKE_TIMEOUT_SECONDS=30")
            .doesNotContain(legacyGlmApiKeyName())
            .doesNotContain("GEMINI_API_KEY");
        assertThat(backendExample)
            .contains("AI_PROVIDER=vertex-ai")
            .contains("GOOGLE_CLOUD_PROJECT_ID=grepp-ai-project")
            .contains("VERTEX_AI_LOCATION=us-central1")
            .contains("VERTEX_AI_MODEL=gemini-2.5-pro")
            .contains("VERTEX_AI_ISSUE_SUMMARY_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_ISSUE_GUIDE_MODEL=gemini-2.5-pro")
            .contains("VERTEX_AI_PR_DRAFT_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_PR_TRANSLATION_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_CREDENTIALS_PATH=./vertex-key.json")
            .contains("LANGFUSE_ENABLED=false")
            .contains("LANGFUSE_BASE_URL=https://cloud.langfuse.com")
            .contains("LANGFUSE_PUBLIC_KEY=")
            .contains("LANGFUSE_SECRET_KEY=")
            .contains("LANGFUSE_SMOKE_ENABLED=false")
            .contains("LANGFUSE_SMOKE_MODE=dry-run")
            .contains("LANGFUSE_SMOKE_TIMEOUT_SECONDS=30")
            .doesNotContain(legacyGlmApiKeyName())
            .doesNotContain("GEMINI_API_KEY");
    }

    @Test
    void runtimeDocsAndDevConfigurationKeepVertexAiDefaultAndRuntimeSmokeAllowsSupportedProviders()
        throws IOException {
        String applicationDev = Files.readString(Path.of("src/main/resources/application-dev.yml"));
        String readme = Files.readString(Path.of("../README.md"));
        String runbook = Files.readString(Path.of("../tools/smoke/AI_RUNTIME_SMOKE.md"));
        String smokeScript = Files.readString(Path.of("../tools/smoke/validate-ai-runtime-env.sh"));

        assertThat(applicationDev)
            .contains("provider: ${AI_PROVIDER:vertex-ai}")
            .doesNotContain(legacyGlmApiKeyName());
        assertThat(readme)
            .contains("[AI 운영 smoke runbook](tools/smoke/AI_RUNTIME_SMOKE.md)")
            .contains("[OCI 배포 문서](deploy/oci/README.md)")
            .doesNotContain(legacyGlmApiKeyName());
        assertThat(runbook)
            .contains("기본 운영 AI provider는 `vertex-ai`입니다.")
            .contains("`AI_PROVIDER=openai-compatible`: OpenAI-compatible chat completion/embedding provider를 사용합니다.")
            .contains("AI_API_BASE_URL=https://api.openai.com/v1")
            .contains("AI_CHAT_MODEL=gpt-4o-mini")
            .contains("AI_EMBEDDING_MODEL=text-embedding-3-small")
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
            .contains("[OCI 배포 문서](deploy/oci/README.md)")
            .doesNotContain(legacyGlmApiKeyName());
        assertThat(runbook)
            .contains("GITHUB_TOKEN_PERMISSIONS=metadata:read,contents:read,pull-requests:read,issues:read")
            .contains("bash tools/smoke/validate-provider-e2e-env.sh")
            .contains("bash tools/smoke/run-provider-e2e-smoke.sh")
            .contains("bash tools/smoke/validate-ai-provider-smoke-env.sh")
            .contains("bash tools/smoke/run-ai-provider-smoke.sh")
            .contains("bash tools/smoke/validate-langfuse-observability-env.sh")
            .contains("bash tools/smoke/run-langfuse-observability-smoke.sh");
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
            .contains("idx_user_profiles_profile_embedding")
            .contains("idx_recommended_issues_issue_embedding")
            .contains("bash tools/test/run-pgvector-integration.sh --validate-only")
            .contains("backfill 후 추천 순위")
            .contains("LANGFUSE_ENABLED=false")
            .contains("PROVIDER_E2E_SMOKE_ENABLED=true")
            .contains("PROVIDER_E2E_AUTH_MODE=oauth-header")
            .contains("PROVIDER_E2E_AUTH_HEADER='Authorization: Bearer")
            .contains("EMBEDDING_BACKFILL_OPERATION_TOKEN=")
            .contains("bash tools/smoke/validate-provider-e2e-env.sh")
            .contains("bash tools/smoke/run-provider-e2e-smoke.sh")
            .contains("bash tools/smoke/validate-ai-provider-smoke-env.sh")
            .contains("bash tools/smoke/run-ai-provider-smoke.sh")
            .contains("LANGFUSE_SMOKE_ENABLED=true")
            .contains("bash tools/smoke/validate-langfuse-observability-env.sh")
            .contains("bash tools/smoke/run-langfuse-observability-smoke.sh")
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
            .contains("PROVIDER_E2E_SMOKE_ENABLED=false")
            .contains("BACKEND_BASE_URL=http://localhost:8080")
            .contains("PROVIDER_E2E_AUTH_MODE=oauth-header")
            .contains("PROVIDER_E2E_AUTH_HEADER=")
            .contains("PROVIDER_E2E_REPO_FULL_NAME=")
            .contains("PROVIDER_E2E_ISSUE_NUMBER=")
            .contains("EMBEDDING_BACKFILL_OPERATION_TOKEN=")
            .contains("EMBEDDING_BACKFILL_MAX_LIMIT=200")
            .contains("EMBEDDING_BACKFILL_MIN_INTERVAL_SECONDS=0")
            .contains("AI_PROVIDER_SMOKE_ENABLED=false")
            .contains("AI_PROVIDER_SMOKE_MODE=dry-run")
            .contains("AI_PROVIDER_SMOKE_TIMEOUT_SECONDS=30")
            .contains("LANGFUSE_SMOKE_ENABLED=false")
            .contains("LANGFUSE_SMOKE_MODE=dry-run")
            .contains("LANGFUSE_SMOKE_TIMEOUT_SECONDS=30")
            .contains("AI_REQUEST_TIMEOUT_MILLIS=30000")
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
            .contains("PROVIDER_E2E_SMOKE_ENABLED=false")
            .contains("BACKEND_BASE_URL=http://localhost:8080")
            .contains("PROVIDER_E2E_AUTH_MODE=oauth-header")
            .contains("PROVIDER_E2E_AUTH_HEADER=")
            .contains("PROVIDER_E2E_REPO_FULL_NAME=")
            .contains("PROVIDER_E2E_ISSUE_NUMBER=")
            .contains("EMBEDDING_BACKFILL_OPERATION_TOKEN=")
            .contains("EMBEDDING_BACKFILL_MAX_LIMIT=200")
            .contains("EMBEDDING_BACKFILL_MIN_INTERVAL_SECONDS=0")
            .contains("AI_PROVIDER_SMOKE_ENABLED=false")
            .contains("AI_PROVIDER_SMOKE_MODE=dry-run")
            .contains("AI_PROVIDER_SMOKE_TIMEOUT_SECONDS=30")
            .contains("LANGFUSE_SMOKE_ENABLED=false")
            .contains("LANGFUSE_SMOKE_MODE=dry-run")
            .contains("LANGFUSE_SMOKE_TIMEOUT_SECONDS=30")
            .contains("AI_REQUEST_TIMEOUT_MILLIS=30000")
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
        Path evidenceFile = Files.createTempFile("provider-e2e-dry-run", ".md");
        SmokeResult result = runProviderE2eRunner(Map.ofEntries(
            entry("PROVIDER_E2E_SMOKE_ENABLED", "true"),
            entry("PROVIDER_E2E_DRY_RUN", "true"),
            entry("PROVIDER_E2E_EVIDENCE_FILE", evidenceFile.toString()),
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

        assertThat(Files.readString(evidenceFile))
            .contains("# Provider E2E Smoke Evidence")
            .contains("- result: dry-run")
            .contains("- provider: openai-compatible")
            .contains("| profile-vector | planned |")
            .contains("| translation | planned |");
    }

    @Test
    void providerE2eSmokeRunnerSkipsAndWritesEvidenceWhenLiveSecretsAreMissing()
        throws IOException, InterruptedException {
        Path evidenceFile = Files.createTempFile("provider-e2e-skip", ".md");

        SmokeResult result = runProviderE2eRunner(Map.ofEntries(
            entry("PROVIDER_E2E_SMOKE_ENABLED", "true"),
            entry("PROVIDER_E2E_DRY_RUN", "false"),
            entry("PROVIDER_E2E_SKIP_ON_MISSING_SECRETS", "true"),
            entry("PROVIDER_E2E_EVIDENCE_FILE", evidenceFile.toString()),
            entry("BACKEND_BASE_URL", "https://api.example.com"),
            entry("AI_PROVIDER", "openai-compatible"),
            entry("AI_CHAT_MODEL", "gpt-test"),
            entry("AI_EMBEDDING_MODEL", "text-embedding-test"),
            entry("AI_EMBEDDING_DIMENSIONS", "1536"),
            entry("RECOMMENDATION_SIMILARITY_PROVIDER", "pgvector"),
            entry("RECOMMENDATION_SIMILARITY_LIMIT", "20"),
            entry("PROVIDER_E2E_REPO_FULL_NAME", "owner/repo"),
            entry("PROVIDER_E2E_ISSUE_NUMBER", "123"),
            entry("PROVIDER_E2E_BASE_BRANCH", "main"),
            entry("PROVIDER_E2E_HEAD_BRANCH", "provider-e2e-smoke")
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("Provider E2E smoke skipped because required live secrets/env are missing.");
        assertThat(Files.readString(evidenceFile))
            .contains("# Provider E2E Smoke Evidence")
            .contains("- result: skipped")
            .contains("- failure_reason: required live secret/env missing")
            .contains("| provider-e2e | skipped |");
    }

    @Test
    void aiProviderSmokeEnvValidationSkipsWhenDisabled() throws IOException, InterruptedException {
        SmokeResult result = runAiProviderSmoke(Map.of());

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("AI provider smoke is disabled.")
            .contains("AI_PROVIDER_SMOKE_ENABLED=true");
    }

    @Test
    void aiProviderSmokeEnvValidationAcceptsOpenAiCompatibleTargetWhenEnabled()
        throws IOException, InterruptedException {
        SmokeResult result = runAiProviderSmoke(Map.ofEntries(
            entry("AI_PROVIDER_SMOKE_ENABLED", "true"),
            entry("AI_PROVIDER_SMOKE_MODE", "dry-run"),
            entry("AI_PROVIDER", "openai-compatible"),
            entry("AI_API_BASE_URL", "https://api.example.com/v1"),
            entry("AI_API_KEY", "test-key"),
            entry("AI_CHAT_MODEL", "gpt-test"),
            entry("AI_EMBEDDING_MODEL", "text-embedding-test"),
            entry("AI_EMBEDDING_DIMENSIONS", "1536"),
            entry("AI_PROVIDER_SMOKE_TIMEOUT_SECONDS", "10")
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("target=openai-compatible")
            .contains("mode=dry-run")
            .contains("timeout=10s");
    }

    @Test
    void aiProviderSmokeEnvValidationAcceptsVertexTargetWhenEnabled()
        throws IOException, InterruptedException {
        SmokeResult result = runAiProviderSmoke(Map.ofEntries(
            entry("AI_PROVIDER_SMOKE_ENABLED", "true"),
            entry("AI_PROVIDER_SMOKE_MODE", "dry-run"),
            entry("AI_PROVIDER", "vertex-ai"),
            entry("GOOGLE_CLOUD_PROJECT_ID", "grepp-ai-project"),
            entry("VERTEX_AI_LOCATION", "us-central1"),
            entry("VERTEX_AI_MODEL", "gemini-2.5-pro"),
            entry("VERTEX_AI_EMBEDDING_MODEL", "gemini-embedding-001"),
            entry("VERTEX_AI_EMBEDDING_DIMENSIONS", "1536"),
            entry("VERTEX_AI_CREDENTIALS_PATH", "/tmp/nonexistent-vertex-key.json"),
            entry("AI_PROVIDER_SMOKE_TIMEOUT_SECONDS", "10")
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("target=vertex-ai")
            .contains("mode=dry-run")
            .contains("timeout=10s");
    }

    @Test
    void aiProviderSmokeEnvValidationRejectsMissingProviderContextWhenEnabled()
        throws IOException, InterruptedException {
        SmokeResult result = runAiProviderSmoke(Map.of(
            "AI_PROVIDER_SMOKE_ENABLED", "true",
            "AI_PROVIDER", "openai-compatible"
        ));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output())
            .contains("missing required env: AI_API_KEY or OPENAI_API_KEY")
            .contains("missing required env: AI_CHAT_MODEL")
            .contains("missing required env: AI_EMBEDDING_MODEL")
            .contains("env must be a positive integer: AI_EMBEDDING_DIMENSIONS")
            .contains("AI provider smoke env validation failed.");
    }

    @Test
    void aiProviderSmokeRunnerDryRunDocumentsProviderVerificationPath()
        throws IOException, InterruptedException {
        SmokeResult result = runAiProviderSmokeRunner(Map.ofEntries(
            entry("AI_PROVIDER_SMOKE_ENABLED", "true"),
            entry("AI_PROVIDER_SMOKE_MODE", "dry-run"),
            entry("AI_PROVIDER", "openai-compatible"),
            entry("AI_API_BASE_URL", "https://api.example.com/v1"),
            entry("AI_API_KEY", "test-key"),
            entry("AI_CHAT_MODEL", "gpt-test"),
            entry("AI_EMBEDDING_MODEL", "text-embedding-test"),
            entry("AI_EMBEDDING_DIMENSIONS", "1536"),
            entry("AI_PROVIDER_SMOKE_TIMEOUT_SECONDS", "10")
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("01 OpenAI-compatible chat completion")
            .contains("02 OpenAI-compatible embedding")
            .contains("03 Vertex AI text generation")
            .contains("04 Vertex AI embedding")
            .contains("05 provider timeout/error observation")
            .contains("06 provider fallback path");
    }

    @Test
    void aiPromptEvaluationFixturesCoverGuideDraftTranslationAndFallbacks() throws IOException {
        String fixtures = Files.readString(Path.of("../tools/smoke/ai-prompt-evaluation-fixtures.json"));

        assertThat(fixtures)
            .contains("\"promptVersion\": \"ai-output-quality-v2026-05-21\"")
            .contains("\"fixture\": \"issue-guide-code-context\"")
            .contains("\"fixture\": \"recommendation-summary-korean\"")
            .contains("\"fixture\": \"recommendation-summary-bad-raw-copy\"")
            .contains("\"fixture\": \"issue-guide-guideline-bad-symbol-list\"")
            .contains("\"fixture\": \"issue-guide-pseudocode-bad-placeholder\"")
            .contains("\"fixture\": \"pr-draft-markdown-close\"")
            .contains("\"fixture\": \"pr-translation-english\"")
            .contains("\"fixture\": \"pr-draft-parser-fallback\"")
            .contains("\"fixture\": \"provider-failure-observation\"")
            .contains("\"requires\": [\"GUIDELINE\", \"PSEUDO_CODE\", \"SIDE_EFFECTS\", \"GIVEN\", \"IF\", \"ASSERT\", \"isBlank()\"]")
            .contains("\"requiresRegex\": [\"template\\\\s*=\\\\s*\\\"\\\\s+\\\"\"]")
            .contains("\"requires\": [\"SUMMARY:\", \"REASON:\", \"isEmpty()\", \"isBlank()\", \"Thymeleaf\"]")
            .contains("\"requiresRegex\": [\"[가-힣]\"]")
            .contains("\"requires\": [\"TITLE\", \"BODY\", \"close #2\", \"## Summary\"]")
            .contains("\"forbids\": [\"README 안내를 보완합니다\"]")
            .contains("apply_smallest_change")
            .contains("\"forbids\": [\"Problem Snapshot\", \"**Description**\", \"GitHub Search\", \"수집된 후보\", \"1. \"]")
            .contains("\"expectedResult\": \"failed\"")
            .contains("\"mockOutput\"")
            .contains("ProductListTest와 shouldRenderProductList(), of(), setVariable()");
    }

    @Test
    void aiPromptEvaluationSmokeDryRunWritesQualityEvidence() throws IOException, InterruptedException {
        Path evidenceFile = Files.createTempFile("ai-prompt-eval", ".md");
        SmokeResult result = runAiPromptEvaluationSmoke(Map.ofEntries(
            entry("AI_PROMPT_EVAL_ENABLED", "true"),
            entry("AI_PROMPT_EVAL_MODE", "dry-run"),
            entry("AI_PROVIDER", "openai-compatible"),
            entry("AI_CHAT_MODEL", "gpt-test"),
            entry("AI_PROMPT_EVAL_EVIDENCE_FILE", evidenceFile.toString())
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("AI prompt evaluation path:")
            .contains("issue-guide-code-context")
            .contains("recommendation-summary-korean")
            .contains("recommendation-summary-bad-raw-copy")
            .contains("issue-guide-guideline-bad-symbol-list")
            .contains("issue-guide-pseudocode-bad-placeholder")
            .contains("pr-draft-markdown-close")
            .contains("pr-translation-english");
        assertThat(Files.readString(evidenceFile))
            .contains("# AI Prompt Evaluation Evidence")
            .contains("- prompt_version: ai-output-quality-v2026-05-21")
            .contains("- model: gpt-test")
            .contains("- target: fixture://ai-prompt-evaluation-fixtures")
            .contains("| issue-guide-code-context | issue-guide | planned | pass | planned |")
            .contains("| recommendation-summary-korean | recommendation-summary | planned | pass | planned |")
            .contains("| recommendation-summary-bad-raw-copy | recommendation-summary | failed | failed | pass |")
            .contains("| issue-guide-guideline-bad-symbol-list | issue-guide | failed | failed | pass |")
            .contains("| issue-guide-pseudocode-bad-placeholder | issue-guide | failed | failed | pass |")
            .contains("forbidden output token(s): Problem Snapshot")
            .contains("forbidden output token(s): apply_smallest_change")
            .contains("expected_result")
            .contains("expectation_result")
            .contains("parser_result");
    }

    @Test
    void langfuseObservabilitySmokeEnvValidationSkipsWhenDisabled() throws IOException, InterruptedException {
        SmokeResult result = runLangfuseObservabilitySmoke(Map.of());

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("Langfuse observability smoke is disabled.")
            .contains("LANGFUSE_SMOKE_ENABLED=true");
    }

    @Test
    void langfuseObservabilitySmokeEnvValidationRejectsMissingEnvWhenEnabled()
        throws IOException, InterruptedException {
        SmokeResult result = runLangfuseObservabilitySmoke(Map.of(
            "LANGFUSE_SMOKE_ENABLED", "true"
        ));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output())
            .contains("missing required env: LANGFUSE_BASE_URL")
            .contains("missing required env: LANGFUSE_PUBLIC_KEY")
            .contains("missing required env: LANGFUSE_SECRET_KEY")
            .contains("env must be a positive integer: LANGFUSE_SMOKE_TIMEOUT_SECONDS")
            .contains("Langfuse observability smoke env validation failed.");
    }

    @Test
    void langfuseObservabilitySmokeEnvValidationAcceptsDryRunAndMasksSecrets()
        throws IOException, InterruptedException {
        SmokeResult result = runLangfuseObservabilitySmoke(Map.ofEntries(
            entry("LANGFUSE_SMOKE_ENABLED", "true"),
            entry("LANGFUSE_SMOKE_MODE", "dry-run"),
            entry("LANGFUSE_BASE_URL", "https://langfuse.example.com"),
            entry("LANGFUSE_PUBLIC_KEY", "pk-live-secret"),
            entry("LANGFUSE_SECRET_KEY", "sk-live-secret"),
            entry("LANGFUSE_ENVIRONMENT", "staging"),
            entry("LANGFUSE_SMOKE_TIMEOUT_SECONDS", "15")
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("target=https://langfuse.example.com")
            .contains("mode=dry-run")
            .contains("environment=staging")
            .contains("timeout=15s")
            .doesNotContain("pk-live-secret")
            .doesNotContain("sk-live-secret");
    }

    @Test
    void langfuseObservabilitySmokeRunnerDryRunDocumentsTraceChecks()
        throws IOException, InterruptedException {
        SmokeResult result = runLangfuseObservabilitySmokeRunnerDryRun();

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("Langfuse observability smoke path:")
            .contains("01 validate Langfuse env")
            .contains("02 send success generation payload")
            .contains("03 send failure generation payload")
            .contains("04 verify prompt version, model, latency, usage, and failure metadata")
            .contains("Langfuse observability smoke dry-run completed.");
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

    @Test
    void providerE2eSmokeWorkflowIsManualAndOptIn() throws IOException {
        String workflow = Files.readString(Path.of("../.github/workflows/provider-e2e-smoke.yml"));

        assertThat(workflow)
            .contains("workflow_dispatch:")
            .doesNotContain("\n  pull_request:")
            .doesNotContain("\n  push:")
            .contains("PROVIDER_E2E_SMOKE_ENABLED: ${{ inputs.enabled }}")
            .contains("PROVIDER_E2E_DRY_RUN: ${{ inputs.dry_run }}")
            .contains("PROVIDER_E2E_AUTH_HEADER: ${{ secrets.PROVIDER_E2E_AUTH_HEADER }}")
            .contains("PROVIDER_E2E_SKIP_ON_MISSING_SECRETS: \"true\"")
            .contains("PROVIDER_E2E_EVIDENCE_FILE: tools/smoke/release-readiness/provider-e2e-smoke-${{ github.run_id }}.md")
            .contains("GITHUB_TOKEN: ${{ secrets.PROVIDER_E2E_GITHUB_TOKEN || github.token }}")
            .contains("EMBEDDING_BACKFILL_OPERATION_TOKEN: ${{ secrets.EMBEDDING_BACKFILL_OPERATION_TOKEN }}")
            .contains("bash tools/smoke/validate-provider-e2e-env.sh")
            .contains("bash tools/smoke/run-provider-e2e-smoke.sh")
            .contains("uses: actions/upload-artifact@b7c566a772e6b6bfb58ed0dc250532a479d7789f")
            .contains("provider-e2e-smoke-evidence");
    }

    @Test
    void aiProviderSmokeWorkflowIsManualAndOptIn() throws IOException {
        String workflow = Files.readString(Path.of("../.github/workflows/ai-provider-smoke.yml"));

        assertThat(workflow)
            .contains("workflow_dispatch:")
            .doesNotContain("\n  pull_request:")
            .doesNotContain("\n  push:")
            .contains("AI_PROVIDER_SMOKE_ENABLED: ${{ inputs.enabled }}")
            .contains("AI_PROVIDER_SMOKE_MODE: ${{ inputs.mode }}")
            .contains("AI_PROVIDER: ${{ inputs.provider }}")
            .contains("AI_REQUEST_TIMEOUT_MILLIS: ${{ vars.AI_REQUEST_TIMEOUT_MILLIS || '30000' }}")
            .contains("AI_PROMPT_EVAL_ENABLED: ${{ inputs.enabled }}")
            .contains("AI_PROMPT_EVAL_MODE: ${{ inputs.mode }}")
            .contains("AI_PROMPT_EVAL_EVIDENCE_FILE: tools/smoke/release-readiness/ai-prompt-evaluation-${{ github.run_id }}.md")
            .contains("bash tools/smoke/validate-ai-provider-smoke-env.sh")
            .contains("bash tools/smoke/run-ai-provider-smoke.sh")
            .contains("node tools/smoke/run-ai-prompt-evaluation-smoke.mjs")
            .contains("uses: actions/upload-artifact@b7c566a772e6b6bfb58ed0dc250532a479d7789f")
            .contains("ai-prompt-evaluation-evidence");
    }

    @Test
    void langfuseObservabilitySmokeWorkflowIsManualAndOptIn() throws IOException {
        String workflow = Files.readString(Path.of("../.github/workflows/langfuse-observability-smoke.yml"));

        assertThat(workflow)
            .contains("workflow_dispatch:")
            .doesNotContain("\n  pull_request:")
            .doesNotContain("\n  push:")
            .contains("LANGFUSE_SMOKE_ENABLED: ${{ inputs.enabled }}")
            .contains("LANGFUSE_SMOKE_MODE: ${{ inputs.mode }}")
            .contains("LANGFUSE_BASE_URL: ${{ vars.LANGFUSE_BASE_URL || 'https://cloud.langfuse.com' }}")
            .contains("LANGFUSE_PUBLIC_KEY: ${{ secrets.LANGFUSE_PUBLIC_KEY }}")
            .contains("LANGFUSE_SECRET_KEY: ${{ secrets.LANGFUSE_SECRET_KEY }}")
            .contains("bash tools/smoke/validate-langfuse-observability-env.sh")
            .contains("bash tools/smoke/run-langfuse-observability-smoke.sh");
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

    private static SmokeResult runAiProviderSmoke(Map<String, String> env) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "../tools/smoke/validate-ai-provider-smoke-env.sh");
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

    private static SmokeResult runAiProviderSmokeRunner(Map<String, String> env)
        throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "../tools/smoke/run-ai-provider-smoke.sh");
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

    private static SmokeResult runAiPromptEvaluationSmoke(Map<String, String> env)
        throws IOException, InterruptedException {
        ProcessBuilder builder =
            new ProcessBuilder("node", "../tools/smoke/run-ai-prompt-evaluation-smoke.mjs");
        Map<String, String> processEnv = builder.environment();
        processEnv.clear();
        processEnv.put("PATH", System.getenv("PATH"));
        processEnv.putAll(env);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(exited).isTrue();
        return new SmokeResult(process.exitValue(), output);
    }

    private static SmokeResult runLangfuseObservabilitySmoke(Map<String, String> env)
        throws IOException, InterruptedException {
        ProcessBuilder builder =
            new ProcessBuilder("/bin/bash", "../tools/smoke/validate-langfuse-observability-env.sh");
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

    private static SmokeResult runLangfuseObservabilitySmokeRunnerDryRun()
        throws IOException, InterruptedException {
        ProcessBuilder builder =
            new ProcessBuilder("/bin/bash", "../tools/smoke/run-langfuse-observability-smoke.sh", "--dry-run");
        Map<String, String> processEnv = builder.environment();
        processEnv.clear();
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
