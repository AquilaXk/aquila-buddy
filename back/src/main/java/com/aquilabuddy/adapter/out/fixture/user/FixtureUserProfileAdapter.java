package com.aquilabuddy.adapter.out.fixture.user;

import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 실제 인증/사용자 저장소 연결 전까지 사용할 사용자 프로필 fixture adapter입니다.
 */
@Component
public class FixtureUserProfileAdapter implements LoadUserProfilePort {

    /**
     * 프론트엔드 프로필 화면과 API 계약 검증에 사용할 고정 프로필을 반환합니다.
     */
    @Override
    public UserProfile loadCurrentUserProfile() {
        return new UserProfile(
            10001L,
            "aquila-dev",
            "Aquila Developer",
            "aquila@example.com",
            List.of("Java", "TypeScript", "SQL"),
            List.of("spring", "nextjs", "open-source"),
            true,
            "2026-05-15T09:00:00Z"
        );
    }
}
