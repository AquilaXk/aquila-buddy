package com.aquilabuddy.application.port.out.user;

import com.aquilabuddy.domain.user.UserProfile;

/**
 * [Outgoing Port] 사용자 프로필 데이터를 외부 저장소에서 읽어오는 계약입니다.
 */
public interface LoadUserProfilePort {

    /**
     * 현재 요청 사용자에 대응하는 프로필 정보를 반환합니다.
     */
    UserProfile loadCurrentUserProfile();
}
