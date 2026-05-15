package com.aquilabuddy.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaOnlySourceTest {

    @Test
    void backendDoesNotContainKotlinSources() throws IOException {
        Path sourceRoot = Path.of("src");

        List<Path> kotlinFiles;
        try (var paths = Files.walk(sourceRoot)) {
            kotlinFiles = paths
                .filter(path -> path.toString().endsWith(".kt"))
                .toList();
        }

        assertThat(kotlinFiles).isEmpty();
    }
}
