package com.aquilabuddy.global.auth.oauth;

import com.aquilabuddy.global.response.ApiResponse;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * dev profile에서 OAuth2 callback query에 담긴 JWT를 JSON으로 확인하는 보조 controller입니다.
 */
@RestController
@Profile("dev")
public class OAuthCallbackController {

    @GetMapping("/oauth/callback")
    ApiResponse<Map<String, String>> callback(@RequestParam String token) {
        return ApiResponse.success(Map.of("token", token));
    }
}
