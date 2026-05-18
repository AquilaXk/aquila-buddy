package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PgvectorInitContractTest {

    @Test
    void initScriptPrintsSchemaApplicationOrderInDryRun() throws IOException, InterruptedException {
        ScriptResult result = runScript("../tools/db/init-postgres-pgvector.sh", "--dry-run");

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("pgvector init plan:")
            .contains("1. back/src/main/resources/schema.sql")
            .contains("2. back/src/main/resources/schema-postgres-pgvector.sql");
    }

    @Test
    void validatorCoversVectorConversionAndHnswIndexContract() throws IOException {
        String validator = Files.readString(Path.of("../tools/db/validate-pgvector-init.mjs"));

        assertThat(validator)
            .contains("idx_user_profiles_profile_embedding")
            .contains("USING hnsw (profile_embedding vector_cosine_ops)")
            .contains("idx_recommended_issues_issue_embedding")
            .contains("USING hnsw (issue_embedding vector_cosine_ops)")
            .contains("schema.sql must be applied before schema-postgres-pgvector.sql");
    }

    private static ScriptResult runScript(String script, String... arguments) throws IOException, InterruptedException {
        String[] command = new String[arguments.length + 2];
        command[0] = "/bin/bash";
        command[1] = script;
        System.arraycopy(arguments, 0, command, 2, arguments.length);

        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> processEnv = builder.environment();
        processEnv.clear();
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(exited).isTrue();
        return new ScriptResult(process.exitValue(), output);
    }

    private record ScriptResult(int exitCode, String output) {
    }
}
