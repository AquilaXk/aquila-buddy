package com.aquilabuddy.adapter.out.fixture.user;

import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.application.port.out.user.RefreshUserProfileVectorPort;
import com.aquilabuddy.application.port.out.user.SaveUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 실제 인증/사용자 저장소 연결 전까지 사용할 사용자 프로필 fixture adapter입니다.
 */
@Component
@Profile("fixture")
public class FixtureUserProfileAdapter implements
    LoadUserProfilePort,
    SaveUserProfilePort,
    RefreshUserProfileVectorPort {

    private static final String FIXTURE_VECTOR_UPDATED_AT = "2026-05-16T00:00:00Z";

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
     * 현재 fixture에 등록된 login과 일치할 때 공개 프로필을 반환합니다.
     */
    @Override
    public Optional<UserProfile> loadUserProfileByLogin(String login) {
        if (currentProfile.login().equals(login)) {
            return Optional.of(currentProfile);
        }

        return Optional.empty();
    }

    /**
     * 현재 fixture에 등록된 GitHub id와 일치할 때 공개 프로필을 반환합니다.
     */
    @Override
    public Optional<UserProfile> loadUserProfileByGithubId(long githubId) {
        if (currentProfile.githubId() == githubId) {
            return Optional.of(currentProfile);
        }

        return Optional.empty();
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

    /**
     * 실제 embedding provider 연결 전까지 추천 준비 상태와 갱신 시각만 deterministic하게 갱신합니다.
     */
    @Override
    public UserProfile refreshCurrentUserProfileVector() {
        currentProfile = new UserProfile(
            currentProfile.githubId(),
            currentProfile.login(),
            currentProfile.name(),
            currentProfile.email(),
            currentProfile.primaryLanguages(),
            currentProfile.topics(),
            true,
            FIXTURE_VECTOR_UPDATED_AT
        );
        return currentProfile;
    }
}
