package com.aquilabuddy.application.port.in.user;

import com.aquilabuddy.domain.user.UserProfile;
import java.util.Optional;

/**
 * [Incoming Port] GitHub 숫자 id로 사용자 공개 프로필을 조회하는 use case 계약입니다.
 */
public interface GetUserProfileByGithubIdUseCase {

    /**
     * GitHub 숫자 id에 대응하는 공개 프로필을 반환합니다.
     */
    Optional<UserProfile> getUserProfileByGithubId(long githubId);
}
