package com.aquilabuddy.global.auth.oauth;

import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * GitHub OAuth2 사용자 응답에서 JWT subject로 사용할 GitHub ID를 추출합니다.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);
        Map<String, Object> attributes = user.getAttributes();
        Object id = attributes.get("id");

        if (id == null) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_info", "GitHub ID를 가져올 수 없습니다.", null)
            );
        }

        return new OAuthPrincipal(String.valueOf(id), attributes);
    }
}
