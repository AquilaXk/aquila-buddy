package com.aquilabuddy.global.auth.oauth;

/**
 * GitHub OAuth access token 응답에서 backend login 처리에 필요한 token 값입니다.
 */
public record GitHubOAuthAccessToken(String accessToken) {
}
