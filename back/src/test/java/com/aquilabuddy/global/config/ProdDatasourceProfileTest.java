package com.aquilabuddy.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProdDatasourceProfileTest {

    @Test
    void prodProfileBindsPostgresDatasourceFromRuntimeEnvironment() throws IOException {
        String applicationProd = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertThat(applicationProd)
            .contains("url: ${DB_URL:jdbc:postgresql://localhost:5432/aquila_buddy}")
            .contains("username: ${DB_USER:aquila}")
            .contains("password: ${DB_PASSWORD:aquila}")
            .contains("driver-class-name: org.postgresql.Driver")
            .contains("ddl-auto: validate")
            .doesNotContain("jdbc:h2:mem");
    }
}
