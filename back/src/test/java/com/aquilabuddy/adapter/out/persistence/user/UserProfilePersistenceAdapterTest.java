package com.aquilabuddy.adapter.out.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import com.aquilabuddy.global.auth.CurrentUserGithubIdProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({UserProfilePersistenceAdapter.class, CurrentUserGithubIdProvider.class})
class UserProfilePersistenceAdapterTest {

    @Autowired
    private UserProfilePersistenceAdapter adapter;

    @Test
    void loadsCurrentUserProfileByDefaultGithubId() {
        UserProfile profile = adapter.loadCurrentUserProfile();

        assertThat(profile.githubId()).isEqualTo(10001L);
        assertThat(profile.login()).isEqualTo("aquila-dev");
        assertThat(profile.primaryLanguages()).containsExactly("Java", "TypeScript", "SQL");
        assertThat(profile.topics()).containsExactly("spring", "nextjs", "open-source");
    }

    @Test
    void loadsUserProfileByLogin() {
        assertThat(adapter.loadUserProfileByLogin("aquila-dev"))
            .get()
            .extracting(UserProfile::githubId)
            .isEqualTo(10001L);
    }

    @Test
    void returnsEmptyWhenLoginDoesNotExist() {
        assertThat(adapter.loadUserProfileByLogin("missing-user")).isEmpty();
    }

    @Test
    void savesCurrentUserProfileUpdate() {
        UserProfile updated = adapter.saveCurrentUserProfile(new UserProfileUpdateRequest(
            "Aquila Maintainer",
            "maintainer@example.com",
            List.of("Java", "TypeScript"),
            List.of("spring", "oss")
        ));

        assertThat(updated.name()).isEqualTo("Aquila Maintainer");
        assertThat(updated.email()).isEqualTo("maintainer@example.com");
        assertThat(updated.primaryLanguages()).containsExactly("Java", "TypeScript");
        assertThat(updated.topics()).containsExactly("spring", "oss");
        assertThat(adapter.loadCurrentUserProfile())
            .usingRecursiveComparison()
            .isEqualTo(updated);
    }

    @Test
    void refreshesCurrentUserProfileVectorState() {
        UserProfile refreshed = adapter.refreshCurrentUserProfileVector();

        assertThat(refreshed.profileReady()).isTrue();
        assertThat(refreshed.vectorUpdatedAt()).isEqualTo("2026-05-16T00:00:00Z");
    }
}
