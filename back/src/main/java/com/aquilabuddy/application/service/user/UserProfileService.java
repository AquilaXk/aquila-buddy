package com.aquilabuddy.application.service.user;

import com.aquilabuddy.application.port.in.user.GetCurrentUserProfileUseCase;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 현재 사용자 프로필 조회 유스케이스를 수행합니다.
 *
 * <p>인증/사용자 저장소 구현은 outbound port 뒤에 두고 프로필 조회 흐름만 담당합니다.
 */
@Service
public class UserProfileService implements GetCurrentUserProfileUseCase {

    private final LoadUserProfilePort loadUserProfilePort;

    public UserProfileService(LoadUserProfilePort loadUserProfilePort) {
        this.loadUserProfilePort = loadUserProfilePort;
    }

    /**
     * 현재 사용자 프로필 조회를 outbound port에 위임합니다.
     */
    @Override
    public UserProfile currentUser() {
        return loadUserProfilePort.loadCurrentUserProfile();
    }
}
