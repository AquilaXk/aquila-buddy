package com.aquilabuddy.adapter.out.fixture.user;

import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.application.port.out.user.SaveUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 실제 인증/사용자 저장소 연결 전까지 사용할 사용자 프로필 fixture adapter입니다.
 */
@Component
public class FixtureUserProfileAdapter implements LoadUserProfilePort, SaveUserProfilePort {

    private UserProfile currentProfile = new UserProfile(
        10001L,
        "aquila-dev",
        "Aquila Developer",
        "aquila@example.com",
        List.of("Java", "TypeScript", "SQL"),
        List.of("spring", "nextjs", "open-source"),
        true,
        "2026-05-15T09:00:00Z"
    );

    /**
     * 프론트엔드 프로필 화면과 API 계약 검증에 사용할 고정 프로필을 반환합니다.
     */
    @Override
    public UserProfile loadCurrentUserProfile() {
        return currentProfile;
    }

    /**
     * 실제 사용자 저장소 연결 전까지 profile 화면의 수정 흐름을 검증할 수 있도록 메모리 상태를 갱신합니다.
     */
    @Override
    public UserProfile saveCurrentUserProfile(UserProfileUpdateRequest request) {
        currentProfile = new UserProfile(
            currentProfile.githubId(),
            currentProfile.login(),
            request.name(),
            request.email(),
            request.primaryLanguages(),
            request.topics(),
            currentProfile.profileReady(),
            currentProfile.vectorUpdatedAt()
        );
        return currentProfile;
    }
}
