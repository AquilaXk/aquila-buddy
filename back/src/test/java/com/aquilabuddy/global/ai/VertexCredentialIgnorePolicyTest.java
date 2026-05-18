package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Vertex AI 서비스 계정 키가 git에 포함되지 않도록 ignore 정책을 검증합니다.
 */
class VertexCredentialIgnorePolicyTest {

    @Test
    void gitignoreExcludesVertexCredentialFiles() throws IOException {
        String gitignore = Files.readString(Path.of("..", ".gitignore"));

        assertThat(gitignore)
            .contains("vertex-key.json")
            .contains("**/*credential*.json")
            .contains("**/*service-account*.json");
    }
}
