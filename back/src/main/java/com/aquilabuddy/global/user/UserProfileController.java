package com.aquilabuddy.global.user;

import com.aquilabuddy.application.user.UserProfileService;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    ApiResponse<UserProfile> me() {
        return ApiResponse.success(userProfileService.currentUser());
    }
}
