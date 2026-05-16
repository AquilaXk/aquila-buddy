package com.aquilabuddy.adapter.out.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class UserProfileJpaRepositoryTest {

    @Autowired
    private UserProfileJpaRepository repository;

    @Test
    void loadsSeedUserProfileWithOrderedLanguagesAndTopics() {
        UserProfileJpaEntity profile = repository.findByLogin("aquila-dev").orElseThrow();

        assertThat(profile.getGithubId()).isEqualTo(10001L);
        assertThat(profile.getName()).isEqualTo("Aquila Developer");
        assertThat(profile.getEmail()).isEqualTo("aquila@example.com");
        assertThat(profile.getPrimaryLanguages()).containsExactly("Java", "TypeScript", "SQL");
        assertThat(profile.getTopics()).containsExactly("spring", "nextjs", "open-source");
        assertThat(profile.isProfileReady()).isTrue();
        assertThat(profile.getVectorUpdatedAt()).isEqualTo("2026-05-15T09:00:00Z");
    }
}
