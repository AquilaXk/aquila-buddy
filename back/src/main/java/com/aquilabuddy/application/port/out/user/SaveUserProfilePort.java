package com.aquilabuddy.application.port.out.user;

import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;

/**
 * [Outgoing Port] 사용자 프로필 수정 내용을 저장하는 계약입니다.
 */
public interface SaveUserProfilePort {

    /**
     * 현재 요청 사용자에 대응하는 프로필을 갱신하고 최신 프로필을 반환합니다.
     */
    UserProfile saveCurrentUserProfile(UserProfileUpdateRequest request);
}
