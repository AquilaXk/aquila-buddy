package com.aquilabuddy.application.port.in.user;

import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;

/**
 * [Incoming Port] 현재 사용자 프로필을 수정하는 use case 계약입니다.
 */
public interface UpdateCurrentUserProfileUseCase {

    /**
     * 요청된 프로필 컨텍스트를 저장하고 최신 사용자 프로필을 반환합니다.
     */
    UserProfile updateCurrentUser(UserProfileUpdateRequest request);
}
