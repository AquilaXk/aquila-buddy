package com.aquilabuddy.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserGithubIdProviderTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsGithubIdFromOAuthPrincipal() {
        OAuthPrincipal principal = new OAuthPrincipal("10001", Map.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));
        CurrentUserGithubIdProvider provider = new CurrentUserGithubIdProvider(42L);

        long githubId = provider.currentGithubId();

        assertThat(githubId).isEqualTo(10001L);
    }

    @Test
    void rejectsMissingAuthenticationInsteadOfUsingDefaultGithubId() {
        CurrentUserGithubIdProvider provider = new CurrentUserGithubIdProvider(10001L);

        assertThatThrownBy(provider::currentGithubId)
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
            .hasMessageContaining("GitHub 사용자 인증 정보");
    }

    @Test
    void rejectsUnsupportedPrincipalInsteadOfUsingDefaultGithubId() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "anonymous",
            "anonymousUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        ));
        CurrentUserGithubIdProvider provider = new CurrentUserGithubIdProvider(10001L);

        assertThatThrownBy(provider::currentGithubId)
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
            .hasMessageContaining("GitHub 사용자 인증 정보");
    }

    @Test
    void rejectsNonNumericAuthenticatedGithubId() {
        OAuthPrincipal principal = new OAuthPrincipal("not-number", Map.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));
        CurrentUserGithubIdProvider provider = new CurrentUserGithubIdProvider(10001L);

        assertThatThrownBy(provider::currentGithubId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GitHub 사용자 id");
    }
}
