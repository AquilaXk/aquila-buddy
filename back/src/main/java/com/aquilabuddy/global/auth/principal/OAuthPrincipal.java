package com.aquilabuddy.global.auth.principal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * OAuth2 로그인과 JWT 인증 흐름에서 공통으로 사용하는 인증 주체입니다.
 */
public class OAuthPrincipal implements OAuth2User {

    private final String githubId;
    private final Map<String, Object> attributes;
    private final List<GrantedAuthority> authorities;

    public OAuthPrincipal(String githubId, Map<String, Object> attributes) {
        this.githubId = githubId;
        this.attributes = Map.copyOf(attributes);
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public String githubId() {
        return githubId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return githubId;
    }
}
