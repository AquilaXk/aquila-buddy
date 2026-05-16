package com.aquilabuddy.adapter.out.persistence.user;

import com.aquilabuddy.domain.user.UserProfile;

/**
 * 사용자 프로필 JPA entity와 도메인 모델 사이의 변환을 담당합니다.
 */
final class UserProfilePersistenceMapper {

    private UserProfilePersistenceMapper() {
    }

    static UserProfile toDomain(UserProfileJpaEntity entity) {
        return new UserProfile(
            entity.getGithubId(),
            entity.getLogin(),
            entity.getName(),
            entity.getEmail(),
            entity.getPrimaryLanguages(),
            entity.getTopics(),
            entity.isProfileReady(),
            entity.getVectorUpdatedAt()
        );
    }
}
