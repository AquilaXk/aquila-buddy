package com.aquilabuddy.application.port.in.user;

import com.aquilabuddy.domain.user.UserProfile;

public interface GetCurrentUserProfileUseCase {

    UserProfile currentUser();
}
