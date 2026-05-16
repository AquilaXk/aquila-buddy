package com.aquilabuddy.application.port.in.user;

import com.aquilabuddy.domain.user.UserProfile;

/**
 * [Incoming Port] 현재 사용자 프로필 기반 vector를 갱신하는 use case 계약입니다.
 */
public interface RefreshCurrentUserProfileVectorUseCase {

    /**
     * 현재 프로필 컨텍스트를 기반으로 추천용 vector 상태를 갱신하고 최신 프로필을 반환합니다.
     */
    UserProfile refreshCurrentUserVector();
}
