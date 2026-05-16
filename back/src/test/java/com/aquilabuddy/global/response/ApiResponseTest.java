package com.aquilabuddy.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successResponseContainsBooleanAndStatusEnvelope() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.message()).isEqualTo("success to response");
    }
}
