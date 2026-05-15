package com.aquilabuddy.application.service.user;

import com.aquilabuddy.application.port.in.user.GetCurrentUserProfileUseCase;
import com.aquilabuddy.domain.user.UserProfile;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService implements GetCurrentUserProfileUseCase {

    @Override
    public UserProfile currentUser() {
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
