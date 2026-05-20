package com.aquilabuddy.adapter.out.persistence.user;

import com.aquilabuddy.domain.user.UserProfile;

/**
 * 사용자 프로필 JPA entity와 도메인 모델 사이의 변환을 담당합니다.
 */
final class UserProfilePersistenceMapper {

    private static final String INITIAL_VECTOR_UPDATED_AT = "1970-01-01T00:00:00Z";

    private UserProfilePersistenceMapper() {
    }

    static UserProfile toDomain(UserProfileJpaEntity entity) {
        return new UserProfile(
            entity.getGithubId(),
            entity.getLogin(),
            entity.getName(),
            entity.getEmail(),
            entity.getAvatarUrl(),
            entity.getPrimaryLanguages(),
            entity.getTopics(),
            entity.isProfileReady(),
            displayableVectorUpdatedAt(entity)
        );
    }

    private static String displayableVectorUpdatedAt(UserProfileJpaEntity entity) {
        if (!entity.isProfileReady() || INITIAL_VECTOR_UPDATED_AT.equals(entity.getVectorUpdatedAt())) {
            return null;
        }

        return entity.getVectorUpdatedAt();
    }
}
