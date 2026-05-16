package com.aquilabuddy.application.port.out.user;

import com.aquilabuddy.domain.user.UserProfile;
import java.util.Optional;

/**
 * [Outgoing Port] 사용자 프로필 데이터를 외부 저장소에서 읽어오는 계약입니다.
 */
public interface LoadUserProfilePort {

    /**
     * 현재 요청 사용자에 대응하는 프로필 정보를 반환합니다.
     */
    UserProfile loadCurrentUserProfile();

    /**
     * GitHub login으로 공개 프로필을 조회합니다.
     */
    default Optional<UserProfile> loadUserProfileByLogin(String login) {
        return Optional.empty();
    }
}
