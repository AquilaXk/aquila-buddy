package com.aquilabuddy.application.port.in.user;

import com.aquilabuddy.domain.user.AuthenticatedUserProfile;

/**
 * 인증 완료된 GitHub 사용자 프로필을 서비스 사용자 저장소와 동기화합니다.
 */
public interface SyncAuthenticatedUserProfileUseCase {

    void syncAuthenticatedUserProfile(AuthenticatedUserProfile profile);
}
