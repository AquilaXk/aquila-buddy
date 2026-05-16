package com.aquilabuddy.application.port.in.user;

import com.aquilabuddy.domain.user.UserProfile;
import java.util.Optional;

/**
 * [Incoming Port] GitHub login 기준 사용자 공개 프로필을 조회하는 유스케이스 계약입니다.
 */
public interface GetUserProfileByLoginUseCase {

    Optional<UserProfile> getUserProfileByLogin(String login);
}
