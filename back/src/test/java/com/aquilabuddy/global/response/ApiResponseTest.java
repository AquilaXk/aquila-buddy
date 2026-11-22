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

    @Test
    void errorResponseContainsBooleanAndStatusEnvelope() {
        ApiResponse<String> response = ApiResponse.error("bad request");

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("fail");
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo("bad request");
    }

    @Test
    void errorResponseUsesDefaultFailMessageWhenMessageIsNull() {
        ApiResponse<String> response = ApiResponse.error(null);

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("fail");
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo("fail");
    }
}
