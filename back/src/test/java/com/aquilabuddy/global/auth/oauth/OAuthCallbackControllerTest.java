package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.global.response.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OAuthCallbackControllerTest {

    @Test
    void returnsTokenInCommonResponse() {
        OAuthCallbackController controller = new OAuthCallbackController();

        ApiResponse<Map<String, String>> response = controller.callback("jwt-token");

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).containsEntry("token", "jwt-token");
        assertThat(response.message()).isEqualTo("success to response");
    }
}
