package com.aquilabuddy.adapter.out.persistence.user;

import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.application.port.out.user.CurrentUserGithubIdPort;
import com.aquilabuddy.application.port.out.user.RefreshUserProfileVectorPort;
import com.aquilabuddy.application.port.out.user.SaveUserProfilePort;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Outgoing Adapter] 사용자 프로필을 JPA 저장소에 보관하고 현재 인증 사용자 기준으로 조회합니다.
 */
@Component
@Transactional
public class UserProfilePersistenceAdapter implements
    LoadUserProfilePort,
    SaveUserProfilePort,
    RefreshUserProfileVectorPort {

    private static final String REFRESHED_VECTOR_UPDATED_AT = "2026-05-16T00:00:00Z";

    private final UserProfileJpaRepository repository;
    private final CurrentUserGithubIdPort currentUserGithubIdPort;

    public UserProfilePersistenceAdapter(
        UserProfileJpaRepository repository,
        CurrentUserGithubIdPort currentUserGithubIdPort
    ) {
        this.repository = repository;
        this.currentUserGithubIdPort = currentUserGithubIdPort;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile loadCurrentUserProfile() {
        return UserProfilePersistenceMapper.toDomain(loadCurrentEntity());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> loadUserProfileByLogin(String login) {
        return repository.findByLogin(login)
            .map(UserProfilePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> loadUserProfileByGithubId(long githubId) {
        return repository.findById(githubId)
            .map(UserProfilePersistenceMapper::toDomain);
    }

    @Override
    public UserProfile saveCurrentUserProfile(UserProfileUpdateRequest request) {
        UserProfileJpaEntity entity = loadCurrentEntity();
        entity.updateProfile(request.name(), request.email(), request.primaryLanguages(), request.topics());

        return UserProfilePersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public UserProfile refreshCurrentUserProfileVector() {
        UserProfileJpaEntity entity = loadCurrentEntity();
        entity.refreshVector(REFRESHED_VECTOR_UPDATED_AT);

        return UserProfilePersistenceMapper.toDomain(repository.save(entity));
    }

    private UserProfileJpaEntity loadCurrentEntity() {
        long githubId = currentUserGithubIdPort.currentGithubId();

        return repository.findById(githubId)
            .orElseThrow(() -> new NoSuchElementException("User profile not found: " + githubId));
    }
}
