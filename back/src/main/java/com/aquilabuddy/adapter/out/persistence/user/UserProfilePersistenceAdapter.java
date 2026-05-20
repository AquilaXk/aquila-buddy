package com.aquilabuddy.adapter.out.persistence.user;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.application.port.out.embedding.BackfillUserProfileEmbeddingPort;
import com.aquilabuddy.application.port.out.user.CurrentUserGithubIdPort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.application.port.out.user.RefreshUserProfileVectorPort;
import com.aquilabuddy.application.port.out.user.SaveUserProfilePort;
import com.aquilabuddy.application.port.out.user.UpsertAuthenticatedUserProfilePort;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillResult;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.user.AuthenticatedUserProfile;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import com.aquilabuddy.global.github.GitHubApiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
    RefreshUserProfileVectorPort,
    UpsertAuthenticatedUserProfilePort,
    BackfillUserProfileEmbeddingPort {

    private static final String INITIAL_VECTOR_UPDATED_AT = "1970-01-01T00:00:00Z";
    private static final String REFRESHED_VECTOR_UPDATED_AT = "2026-05-16T00:00:00Z";
    private static final int MAX_FAILURE_REASON_SAMPLES = 10;

    private final UserProfileJpaRepository repository;
    private final CurrentUserGithubIdPort currentUserGithubIdPort;
    private final GitHubApiClient gitHubApiClient;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;
    private final boolean userContextEnabled;
    private final int expectedEmbeddingDimensions;

    public UserProfilePersistenceAdapter(
        UserProfileJpaRepository repository,
        CurrentUserGithubIdPort currentUserGithubIdPort,
        GitHubApiClient gitHubApiClient,
        ObjectProvider<EmbeddingClient> embeddingClientProvider,
        @Value("${app.github.user-context.enabled:false}") boolean userContextEnabled,
        @Value("${app.embedding.expected-dimensions:1536}") int expectedEmbeddingDimensions
    ) {
        this.repository = repository;
        this.currentUserGithubIdPort = currentUserGithubIdPort;
        this.gitHubApiClient = gitHubApiClient;
        this.embeddingClientProvider = embeddingClientProvider;
        this.userContextEnabled = userContextEnabled;
        this.expectedEmbeddingDimensions = expectedEmbeddingDimensions;
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
    public void upsertAuthenticatedUserProfile(AuthenticatedUserProfile profile) {
        UserProfileJpaEntity entity = repository.findById(profile.githubId())
            .map(existing -> {
                existing.updateIdentity(profile.login(), profile.name(), profile.email(), profile.avatarUrl());
                return existing;
            })
            .orElseGet(() -> new UserProfileJpaEntity(
                profile.githubId(),
                profile.login(),
                profile.name(),
                profile.email(),
                profile.avatarUrl(),
                List.of(),
                List.of(),
                false,
                INITIAL_VECTOR_UPDATED_AT
            ));

        repository.save(entity);
    }

    @Override
    public UserProfile refreshCurrentUserProfileVector() {
        UserProfileJpaEntity entity = loadCurrentEntity();

        if (userContextEnabled) {
            GitHubApiClient.GitHubContributionContext context = gitHubApiClient.loadContributionContext(entity.getLogin());
            entity.refreshVector(
                REFRESHED_VECTOR_UPDATED_AT,
                nonEmptyOrCurrent(context.primaryLanguages(), entity.getPrimaryLanguages()),
                nonEmptyOrCurrent(context.topics(), entity.getTopics())
            );
        } else {
            entity.refreshVector(REFRESHED_VECTOR_UPDATED_AT);
        }
        entity.updateProfileEmbedding(createProfileEmbedding(entity));

        return UserProfilePersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public EmbeddingBackfillResult backfillMissingProfileEmbeddings(int limit) {
        int requestedLimit = normalizeLimit(limit);
        List<UserProfileJpaEntity> candidates = repository.findByProfileEmbeddingIsNullOrderByGithubIdAsc(
            PageRequest.of(0, requestedLimit)
        );
        int skippedExisting = saturatedInt(repository.countByProfileEmbeddingIsNotNull());
        EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            return new EmbeddingBackfillResult(
                "profiles",
                requestedLimit,
                candidates.size(),
                0,
                skippedExisting,
                0,
                candidates.size(),
                0,
                0
            );
        }

        int updated = 0;
        int skippedBlankText = 0;
        int skippedDimensionMismatch = 0;
        int failed = 0;
        List<String> failureReasons = new ArrayList<>();
        for (UserProfileJpaEntity entity : candidates) {
            String input = buildProfileEmbeddingInput(entity);
            if (input.isBlank()) {
                skippedBlankText++;
                continue;
            }
            try {
                EmbeddingVector vector = embeddingClient.embed(input);
                if (vector.dimension() != expectedEmbeddingDimensions) {
                    skippedDimensionMismatch++;
                    addFailureReason(
                        failureReasons,
                        profileLabel(entity) + ": expected " + expectedEmbeddingDimensions
                            + " dimensions but was " + vector.dimension()
                    );
                    continue;
                }
                entity.updateProfileEmbedding(vector.toPgvectorLiteral());
                updated++;
            } catch (RuntimeException exception) {
                failed++;
                addFailureReason(failureReasons, profileLabel(entity) + ": " + exception.getClass().getSimpleName());
            }
        }
        repository.saveAll(candidates);

        return new EmbeddingBackfillResult(
            "profiles",
            requestedLimit,
            candidates.size(),
            updated,
            skippedExisting,
            skippedBlankText,
            0,
            skippedDimensionMismatch,
            failed,
            failureReasons
        );
    }

    private String createProfileEmbedding(UserProfileJpaEntity entity) {
        EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            return null;
        }

        try {
            EmbeddingVector vector = embeddingClient.embed(buildProfileEmbeddingInput(entity));
            if (vector.dimension() != expectedEmbeddingDimensions) {
                return null;
            }
            return vector.toPgvectorLiteral();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String buildProfileEmbeddingInput(UserProfileJpaEntity entity) {
        return String.join("\n",
            "Login: " + entity.getLogin(),
            "Name: " + entity.getName(),
            "Email: " + entity.getEmail(),
            "Primary languages: " + String.join(", ", entity.getPrimaryLanguages()),
            "Topics: " + String.join(", ", entity.getTopics())
        ).trim();
    }

    private List<String> nonEmptyOrCurrent(List<String> items, List<String> currentItems) {
        if (items == null || items.isEmpty()) {
            return currentItems;
        }

        return items;
    }

    private UserProfileJpaEntity loadCurrentEntity() {
        long githubId = currentUserGithubIdPort.currentGithubId();

        return repository.findById(githubId)
            .orElseThrow(() -> new NoSuchElementException("User profile not found: " + githubId));
    }

    private static int normalizeLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 500);
    }

    private static int saturatedInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static String profileLabel(UserProfileJpaEntity entity) {
        return entity.getGithubId() + "/" + entity.getLogin();
    }

    private static void addFailureReason(List<String> failureReasons, String reason) {
        if (failureReasons.size() < MAX_FAILURE_REASON_SAMPLES) {
            failureReasons.add(reason);
        }
    }
}
