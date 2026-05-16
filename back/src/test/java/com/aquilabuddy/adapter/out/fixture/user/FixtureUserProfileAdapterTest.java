package com.aquilabuddy.adapter.out.fixture.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.user.UserProfile;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FixtureUserProfileAdapterTest {

    @Test
    void loadsUserProfileByLogin() {
        FixtureUserProfileAdapter adapter = new FixtureUserProfileAdapter();

        Optional<UserProfile> profile = adapter.loadUserProfileByLogin("aquila-dev");

        assertThat(profile).isPresent();
        assertThat(profile.get().login()).isEqualTo("aquila-dev");
        assertThat(profile.get().githubId()).isEqualTo(10001L);
    }

    @Test
    void returnsEmptyWhenLoginDoesNotMatchFixture() {
        FixtureUserProfileAdapter adapter = new FixtureUserProfileAdapter();

        Optional<UserProfile> profile = adapter.loadUserProfileByLogin("missing-user");

        assertThat(profile).isEmpty();
    }
}
