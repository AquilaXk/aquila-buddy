package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.global.response.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OAuthCallbackControllerTest {

    @Test
    void returnsCookieIssuedStatusInCommonResponse() {
        OAuthCallbackController controller = new OAuthCallbackController();

        ApiResponse<Map<String, String>> response = controller.callback();

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).containsEntry("status", "cookie-issued");
        assertThat(response.message()).isEqualTo("success to response");
    }
}
