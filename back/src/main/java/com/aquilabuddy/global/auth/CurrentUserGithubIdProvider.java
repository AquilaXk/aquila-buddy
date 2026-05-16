package com.aquilabuddy.global.auth;

import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 현재 요청의 인증 주체에서 GitHub 사용자 id를 추출합니다.
 */
@Component
public class CurrentUserGithubIdProvider {

    private final long defaultGithubId;

    public CurrentUserGithubIdProvider(
        @Value("${app.auth.default-github-id:10001}") long defaultGithubId
    ) {
        this.defaultGithubId = defaultGithubId;
    }

    public long currentGithubId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof OAuthPrincipal principal)) {
            return defaultGithubId;
        }

        try {
            return Long.parseLong(principal.githubId());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("GitHub 사용자 id는 숫자여야 합니다.", exception);
        }
    }
}
