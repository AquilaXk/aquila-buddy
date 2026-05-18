package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;

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
            .contains("LANGFUSE_ENABLED=true")
            .contains("Langfuse tracing은 선택 기능")
            .doesNotContain("AI_PROVIDER=openai-compatible")
            .doesNotContain(legacyGlmApiKeyName());
        assertThat(smokeScript)
            .contains("provider=\"${AI_PROVIDER:-vertex-ai}\"")
            .contains("AI_PROVIDER must be vertex-ai or openai-compatible for runtime smoke.")
            .doesNotContain(legacyGlmApiKeyName());
    }

    @Test
    void rootEnvExampleDocumentsOpenAiCompatibleRuntimeSettings() throws IOException {
        String rootExample = Files.readString(Path.of("../.env.example"));

        assertThat(rootExample)
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
            "GITHUB_TOKEN", "github-token"
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
            .contains("AI runtime smoke env validation failed.");
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

    private record SmokeResult(int exitCode, String output) {
    }

}
