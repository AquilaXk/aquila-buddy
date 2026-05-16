package com.aquilabuddy.application.service.user;

import com.aquilabuddy.application.port.in.user.GetCurrentUserProfileUseCase;
import com.aquilabuddy.application.port.in.user.GetUserProfileByLoginUseCase;
import com.aquilabuddy.application.port.in.user.RefreshCurrentUserProfileVectorUseCase;
import com.aquilabuddy.application.port.in.user.UpdateCurrentUserProfileUseCase;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.application.port.out.user.RefreshUserProfileVectorPort;
import com.aquilabuddy.application.port.out.user.SaveUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 현재 사용자 프로필 조회 유스케이스를 수행합니다.
 *
 * <p>인증/사용자 저장소 구현은 outbound port 뒤에 두고 프로필 조회 흐름만 담당합니다.
 */
@Service
public class UserProfileService implements
    GetCurrentUserProfileUseCase,
    GetUserProfileByLoginUseCase,
    UpdateCurrentUserProfileUseCase,
    RefreshCurrentUserProfileVectorUseCase {

    private final LoadUserProfilePort loadUserProfilePort;
    private final SaveUserProfilePort saveUserProfilePort;
    private final RefreshUserProfileVectorPort refreshUserProfileVectorPort;

    public UserProfileService(
        LoadUserProfilePort loadUserProfilePort,
        SaveUserProfilePort saveUserProfilePort,
        RefreshUserProfileVectorPort refreshUserProfileVectorPort
    ) {
        this.loadUserProfilePort = loadUserProfilePort;
        this.saveUserProfilePort = saveUserProfilePort;
        this.refreshUserProfileVectorPort = refreshUserProfileVectorPort;
    }

    /**
     * 현재 사용자 프로필 조회를 outbound port에 위임합니다.
     */
    @Override
    public UserProfile currentUser() {
        return loadUserProfilePort.loadCurrentUserProfile();
    }

    /**
     * GitHub login 기준 공개 프로필 조회를 outbound port에 위임합니다.
     */
    @Override
    public Optional<UserProfile> getUserProfileByLogin(String login) {
        return loadUserProfilePort.loadUserProfileByLogin(login);
    }

    /**
     * 현재 사용자 프로필 수정 요청을 저장 port에 위임하고 최신 프로필을 반환합니다.
     */
    @Override
    public UserProfile updateCurrentUser(UserProfileUpdateRequest request) {
        return saveUserProfilePort.saveCurrentUserProfile(request);
    }

    /**
     * 현재 사용자 프로필 기반 vector 갱신을 outbound adapter에 위임합니다.
     */
    @Override
    public UserProfile refreshCurrentUserVector() {
        return refreshUserProfileVectorPort.refreshCurrentUserProfileVector();
    }
}
