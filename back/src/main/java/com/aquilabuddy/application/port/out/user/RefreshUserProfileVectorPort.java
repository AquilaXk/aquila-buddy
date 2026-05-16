package com.aquilabuddy.application.port.out.user;

import com.aquilabuddy.domain.user.UserProfile;

/**
 * [Outgoing Port] 사용자 프로필 vector 갱신을 수행하는 계약입니다.
 */
public interface RefreshUserProfileVectorPort {

    /**
     * 현재 요청 사용자 프로필 기반 vector를 갱신하고 최신 프로필을 반환합니다.
     */
    UserProfile refreshCurrentUserProfileVector();
}
