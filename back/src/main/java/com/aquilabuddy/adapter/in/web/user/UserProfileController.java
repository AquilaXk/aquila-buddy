package com.aquilabuddy.adapter.in.web.user;

import com.aquilabuddy.application.port.in.user.GetCurrentUserProfileUseCase;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final GetCurrentUserProfileUseCase getCurrentUserProfileUseCase;

    public UserProfileController(GetCurrentUserProfileUseCase getCurrentUserProfileUseCase) {
        this.getCurrentUserProfileUseCase = getCurrentUserProfileUseCase;
    }

    @GetMapping("/me")
    ApiResponse<UserProfile> me() {
        return ApiResponse.success(getCurrentUserProfileUseCase.currentUser());
    }
}
