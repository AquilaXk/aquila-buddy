package com.aquilabuddy.adapter.in.web.user;

import com.aquilabuddy.application.port.in.user.GetCurrentUserProfileUseCase;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Incoming Web Adapter] 현재 사용자 프로필 조회 API 요청을 처리하는 REST controller입니다.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final GetCurrentUserProfileUseCase getCurrentUserProfileUseCase;

    public UserProfileController(GetCurrentUserProfileUseCase getCurrentUserProfileUseCase) {
        this.getCurrentUserProfileUseCase = getCurrentUserProfileUseCase;
    }

    /**
     * 로그인 사용자 기준의 프로필 요약 정보를 공통 응답 포맷으로 반환합니다.
     */
    @GetMapping("/me")
    ApiResponse<UserProfile> me() {
        return ApiResponse.success(getCurrentUserProfileUseCase.currentUser());
    }
}
