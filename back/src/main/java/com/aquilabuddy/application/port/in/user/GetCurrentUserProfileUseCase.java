package com.aquilabuddy.application.port.in.user;

import com.aquilabuddy.domain.user.UserProfile;

/**
 * [Incoming Port] 현재 사용자 프로필을 조회하는 유스케이스 계약입니다.
 */
public interface GetCurrentUserProfileUseCase {

    /**
     * 요청 컨텍스트의 현재 사용자 프로필을 반환합니다.
     */
    UserProfile currentUser();
}
