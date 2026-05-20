package com.aquilabuddy.global.auth.refresh;

public record RefreshTokenSession(
    String githubId,
    String refreshToken
) {
}
