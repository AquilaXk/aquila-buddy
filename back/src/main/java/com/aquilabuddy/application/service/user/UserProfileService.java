package com.aquilabuddy.application.service.user;

import com.aquilabuddy.application.port.in.user.GetCurrentUserProfileUseCase;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService implements GetCurrentUserProfileUseCase {

    private final LoadUserProfilePort loadUserProfilePort;

    public UserProfileService(LoadUserProfilePort loadUserProfilePort) {
        this.loadUserProfilePort = loadUserProfilePort;
    }

    @Override
    public UserProfile currentUser() {
        return loadUserProfilePort.loadCurrentUserProfile();
    }
}
