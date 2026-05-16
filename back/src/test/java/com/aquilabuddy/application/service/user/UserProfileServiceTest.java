package com.aquilabuddy.application.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.application.port.out.user.SaveUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 사용자 프로필 application service가 port 경계에만 의존해 조회/수정 흐름을 조합하는지 검증합니다.
 */
class UserProfileServiceTest {

    @Test
    void updatesCurrentUserProfile() {
        RecordingUserProfilePort port = new RecordingUserProfilePort();
        UserProfileService service = new UserProfileService(port, port);

        UserProfile updated = service.updateCurrentUser(new UserProfileUpdateRequest(
            "Aquila Maintainer",
            "maintainer@example.com",
            List.of("Java", "TypeScript"),
            List.of("spring", "oss")
        ));

        assertThat(port.savedRequest).isEqualTo(new UserProfileUpdateRequest(
            "Aquila Maintainer",
            "maintainer@example.com",
            List.of("Java", "TypeScript"),
            List.of("spring", "oss")
        ));
        assertThat(updated.name()).isEqualTo("Aquila Maintainer");
        assertThat(updated.email()).isEqualTo("maintainer@example.com");
        assertThat(updated.primaryLanguages()).containsExactly("Java", "TypeScript");
        assertThat(updated.topics()).containsExactly("spring", "oss");
    }

    private static class RecordingUserProfilePort implements LoadUserProfilePort, SaveUserProfilePort {

        private UserProfileUpdateRequest savedRequest;

        @Override
        public UserProfile loadCurrentUserProfile() {
            return new UserProfile(
                10001L,
                "aquila-dev",
                "Aquila Developer",
                "aquila@example.com",
                List.of("Java"),
                List.of("spring"),
                true,
                "2026-05-15T09:00:00Z"
            );
        }

        @Override
        public UserProfile saveCurrentUserProfile(UserProfileUpdateRequest request) {
            this.savedRequest = request;
            return new UserProfile(
                10001L,
                "aquila-dev",
                request.name(),
                request.email(),
                request.primaryLanguages(),
                request.topics(),
                true,
                "2026-05-15T09:00:00Z"
            );
        }
    }
}
