package com.aquilabuddy.application.port.out.user;

import com.aquilabuddy.domain.user.AuthenticatedUserProfile;

/**
 * 인증 사용자 프로필을 생성하거나 최신 GitHub 식별 정보로 갱신합니다.
 */
public interface UpsertAuthenticatedUserProfilePort {

    void upsertAuthenticatedUserProfile(AuthenticatedUserProfile profile);
}
