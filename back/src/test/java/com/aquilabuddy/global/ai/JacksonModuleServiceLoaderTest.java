package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.Module;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class JacksonModuleServiceLoaderTest {

    @Test
    void resolvesJacksonModulesAdvertisedByRuntimeDependencies() {
        assertThatCode(() -> ServiceLoader.load(Module.class).stream().toList())
            .doesNotThrowAnyException();
    }
}
