package com.aquilabuddy.adapter.in.web.user;

import com.aquilabuddy.application.port.in.user.GetCurrentUserProfileUseCase;
import com.aquilabuddy.application.port.in.user.GetUserProfileByLoginUseCase;
import com.aquilabuddy.application.port.in.user.RefreshCurrentUserProfileVectorUseCase;
import com.aquilabuddy.application.port.in.user.UpdateCurrentUserProfileUseCase;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import com.aquilabuddy.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * [Incoming Web Adapter] 현재 사용자 프로필 조회 API 요청을 처리하는 REST controller입니다.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final GetCurrentUserProfileUseCase getCurrentUserProfileUseCase;
    private final GetUserProfileByLoginUseCase getUserProfileByLoginUseCase;
    private final UpdateCurrentUserProfileUseCase updateCurrentUserProfileUseCase;
    private final RefreshCurrentUserProfileVectorUseCase refreshCurrentUserProfileVectorUseCase;

    public UserProfileController(
        GetCurrentUserProfileUseCase getCurrentUserProfileUseCase,
        GetUserProfileByLoginUseCase getUserProfileByLoginUseCase,
        UpdateCurrentUserProfileUseCase updateCurrentUserProfileUseCase,
        RefreshCurrentUserProfileVectorUseCase refreshCurrentUserProfileVectorUseCase
    ) {
        this.getCurrentUserProfileUseCase = getCurrentUserProfileUseCase;
        this.getUserProfileByLoginUseCase = getUserProfileByLoginUseCase;
        this.updateCurrentUserProfileUseCase = updateCurrentUserProfileUseCase;
        this.refreshCurrentUserProfileVectorUseCase = refreshCurrentUserProfileVectorUseCase;
    }

    /**
     * 로그인 사용자 기준의 프로필 요약 정보를 공통 응답 포맷으로 반환합니다.
     */
    @GetMapping("/me")
    ApiResponse<UserProfile> me() {
        return ApiResponse.success(getCurrentUserProfileUseCase.currentUser());
    }

    /**
     * GitHub login으로 사용자 공개 프로필을 조회합니다.
     */
    @GetMapping("/{githubId}")
    ApiResponse<UserProfile> userByGithubId(@PathVariable String githubId) {
        UserProfile profile = getUserProfileByLoginUseCase.getUserProfileByLogin(githubId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return ApiResponse.success(profile);
    }

    /**
     * 현재 사용자 프로필의 추천 컨텍스트를 수정하고 최신 프로필을 반환합니다.
     */
    @PatchMapping("/me")
    ApiResponse<UserProfile> updateMe(@Valid @RequestBody UpdateCurrentUserProfileRequest request) {
        return ApiResponse.success(updateCurrentUserProfileUseCase.updateCurrentUser(request.toDomain()));
    }

    /**
     * 현재 프로필 기반 추천 vector 상태를 갱신하고 최신 프로필을 반환합니다.
     */
    @PostMapping("/me/vector")
    ApiResponse<UserProfile> refreshVector() {
        return ApiResponse.success(refreshCurrentUserProfileVectorUseCase.refreshCurrentUserVector());
    }

    /**
     * web 요청 body를 domain 요청 모델로 변환하기 전 프로필 수정 입력을 검증합니다.
     */
    record UpdateCurrentUserProfileRequest(
        @NotBlank(message = "name은 비어 있을 수 없습니다.")
        String name,
        @NotBlank(message = "email은 비어 있을 수 없습니다.")
        @Email(message = "email 형식이 올바르지 않습니다.")
        String email,
        @NotEmpty(message = "primaryLanguages는 비어 있을 수 없습니다.")
        List<@NotBlank(message = "primaryLanguages 항목은 비어 있을 수 없습니다.") String> primaryLanguages,
        @NotEmpty(message = "topics는 비어 있을 수 없습니다.")
        List<@NotBlank(message = "topics 항목은 비어 있을 수 없습니다.") String> topics
    ) {

        UserProfileUpdateRequest toDomain() {
            return new UserProfileUpdateRequest(name, email, primaryLanguages, topics);
        }
    }
}
