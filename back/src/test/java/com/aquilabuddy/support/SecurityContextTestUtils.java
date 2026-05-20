package com.aquilabuddy.support;

import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 필터를 끈 web/persistence 테스트에서 현재 GitHub 사용자를 명시적으로 주입합니다.
 */
public final class SecurityContextTestUtils {

    private SecurityContextTestUtils() {
    }

    public static void setCurrentGithubUser(long githubId) {
        OAuthPrincipal principal = new OAuthPrincipal(String.valueOf(githubId), Map.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
