package com.aquilabuddy.global.auth.oauth;

/**
 * GitHub OAuth user endpoint에서 받은 인증 사용자 식별 정보입니다.
 */
public record GitHubOAuthUser(
    long id,
    String login,
    String name,
    String email
) {
}
