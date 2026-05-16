package com.aquilabuddy.application.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.application.port.out.user.RefreshUserProfileVectorPort;
import com.aquilabuddy.application.port.out.user.SaveUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 사용자 프로필 application service가 port 경계에만 의존해 조회/수정 흐름을 조합하는지 검증합니다.
 */
class UserProfileServiceTest {

    @Test
    void updatesCurrentUserProfile() {
        RecordingUserProfilePort port = new RecordingUserProfilePort();
        UserProfileService service = new UserProfileService(port, port, port);

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

    @Test
    void refreshesCurrentUserProfileVector() {
        RecordingUserProfilePort port = new RecordingUserProfilePort();
        UserProfileService service = new UserProfileService(port, port, port);

        UserProfile refreshed = service.refreshCurrentUserVector();

        assertThat(port.refreshRequested).isTrue();
        assertThat(refreshed.profileReady()).isTrue();
        assertThat(refreshed.vectorUpdatedAt()).isEqualTo("2026-05-16T00:00:00Z");
    }

    @Test
    void returnsUserProfileByLogin() {
        RecordingUserProfilePort port = new RecordingUserProfilePort();
        UserProfileService service = new UserProfileService(port, port, port);

        Optional<UserProfile> profile = service.getUserProfileByLogin("aquila-dev");

        assertThat(profile).isPresent();
        assertThat(profile.get().login()).isEqualTo("aquila-dev");
        assertThat(port.requestedLogin).isEqualTo("aquila-dev");
    }

    @Test
    void returnsUserProfileByGithubId() {
        RecordingUserProfilePort port = new RecordingUserProfilePort();
        UserProfileService service = new UserProfileService(port, port, port);

        Optional<UserProfile> profile = service.getUserProfileByGithubId(10001L);

        assertThat(profile).isPresent();
        assertThat(profile.get().login()).isEqualTo("aquila-dev");
        assertThat(port.requestedGithubId).isEqualTo(10001L);
    }

    @Test
    void returnsEmptyWhenUserProfileByLoginDoesNotExist() {
        RecordingUserProfilePort port = new RecordingUserProfilePort();
        UserProfileService service = new UserProfileService(port, port, port);

        Optional<UserProfile> profile = service.getUserProfileByLogin("missing-user");

        assertThat(profile).isEmpty();
        assertThat(port.requestedLogin).isEqualTo("missing-user");
    }

    @Test
    void returnsEmptyWhenUserProfileByGithubIdDoesNotExist() {
        RecordingUserProfilePort port = new RecordingUserProfilePort();
        UserProfileService service = new UserProfileService(port, port, port);

        Optional<UserProfile> profile = service.getUserProfileByGithubId(99999L);

        assertThat(profile).isEmpty();
        assertThat(port.requestedGithubId).isEqualTo(99999L);
    }

    private static class RecordingUserProfilePort implements
        LoadUserProfilePort,
        SaveUserProfilePort,
        RefreshUserProfileVectorPort {

        private UserProfileUpdateRequest savedRequest;
        private boolean refreshRequested;
        private String requestedLogin;
        private long requestedGithubId;

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
        public Optional<UserProfile> loadUserProfileByLogin(String login) {
            this.requestedLogin = login;
            if (!"aquila-dev".equals(login)) {
                return Optional.empty();
            }

            return Optional.of(loadCurrentUserProfile());
        }

        @Override
        public Optional<UserProfile> loadUserProfileByGithubId(long githubId) {
            this.requestedGithubId = githubId;
            if (githubId != 10001L) {
                return Optional.empty();
            }

            return Optional.of(loadCurrentUserProfile());
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

        @Override
        public UserProfile refreshCurrentUserProfileVector() {
            this.refreshRequested = true;
            return new UserProfile(
                10001L,
                "aquila-dev",
                "Aquila Developer",
                "aquila@example.com",
                List.of("Java"),
                List.of("spring"),
                true,
                "2026-05-16T00:00:00Z"
            );
        }
    }
}
