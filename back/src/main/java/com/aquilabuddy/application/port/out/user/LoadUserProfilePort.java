package com.aquilabuddy.application.port.out.user;

import com.aquilabuddy.domain.user.UserProfile;

public interface LoadUserProfilePort {

    UserProfile loadCurrentUserProfile();
}
