package com.aquilabuddy.adapter.out.fixture.user;

import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FixtureUserProfileAdapter implements LoadUserProfilePort {

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
