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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * [Outgoing Adapter] 사용자 프로필을 JPA 저장소에 보관하고 현재 인증 사용자 기준으로 조회합니다.
 *
 * <p>GitHub·임베딩 외부 API 호출은 DB 트랜잭션 밖에서 실행하고 DB 쓰기만 짧은 트랜잭션으로 묶습니다.
 */
@Component
public class UserProfilePersistenceAdapter implements
    LoadUserProfilePort,
    SaveUserProfilePort,
    RefreshUserProfileVectorPort,
    UpsertAuthenticatedUserProfilePort,
    BackfillUserProfileEmbeddingPort {

    private static final String INITIAL_VECTOR_UPDATED_AT = "1970-01-01T00:00:00Z";
    private static final int MAX_FAILURE_REASON_SAMPLES = 10;

    private final UserProfileJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserGithubIdPort currentUserGithubIdPort;
    private final GitHubApiClient gitHubApiClient;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;
    private final boolean userContextEnabled;
    private final int expectedEmbeddingDimensions;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private volatile Boolean pgvectorProfileEmbeddingColumn;

    public UserProfilePersistenceAdapter(
        UserProfileJpaRepository repository,
        JdbcTemplate jdbcTemplate,
        CurrentUserGithubIdPort currentUserGithubIdPort,
        GitHubApiClient gitHubApiClient,
        ObjectProvider<EmbeddingClient> embeddingClientProvider,
        @Value("${app.github.user-context.enabled:false}") boolean userContextEnabled,
        @Value("${app.embedding.expected-dimensions:1536}") int expectedEmbeddingDimensions
    ) {
        this(
            repository,
            jdbcTemplate,
            currentUserGithubIdPort,
            gitHubApiClient,
            embeddingClientProvider,
            userContextEnabled,
            expectedEmbeddingDimensions,
            null
        );
    }

    @Autowired
    public UserProfilePersistenceAdapter(
        UserProfileJpaRepository repository,
        JdbcTemplate jdbcTemplate,
        CurrentUserGithubIdPort currentUserGithubIdPort,
        GitHubApiClient gitHubApiClient,
        ObjectProvider<EmbeddingClient> embeddingClientProvider,
        @Value("${app.github.user-context.enabled:false}") boolean userContextEnabled,
        @Value("${app.embedding.expected-dimensions:1536}") int expectedEmbeddingDimensions,
        @Autowired(required = false) PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserGithubIdPort = currentUserGithubIdPort;
        this.gitHubApiClient = gitHubApiClient;
        this.embeddingClientProvider = embeddingClientProvider;
        this.userContextEnabled = userContextEnabled;
        this.expectedEmbeddingDimensions = expectedEmbeddingDimensions;
        if (transactionManager == null) {
            this.writeTransactionTemplate = null;
            this.readTransactionTemplate = null;
        } else {
            this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
            TransactionTemplate readTx = new TransactionTemplate(transactionManager);
            readTx.setReadOnly(true);
            this.readTransactionTemplate = readTx;
        }
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
    @Transactional
    public UserProfile saveCurrentUserProfile(UserProfileUpdateRequest request) {
        UserProfileJpaEntity entity = loadCurrentEntity();
        entity.updateProfile(request.name(), request.email(), request.primaryLanguages(), request.topics());

        return UserProfilePersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public void upsertAuthenticatedUserProfile(AuthenticatedUserProfile profile) {
        Optional<UserProfileJpaEntity> existingByGithubId = repository.findById(profile.githubId());
        if (existingByGithubId.isPresent()) {
            UserProfileJpaEntity entity = existingByGithubId.get();
            retireConflictingLoginOwner(profile.login(), profile.githubId());
            entity.updateIdentity(profile.login(), profile.name(), profile.email(), profile.avatarUrl());
            repository.save(entity);
            return;
        }

        UserProfileJpaEntity entity = repository.findByLogin(profile.login())
            .map(existing -> migrateLoginOwnerToCanonicalGithubId(existing, profile))
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

    private UserProfileJpaEntity migrateLoginOwnerToCanonicalGithubId(
        UserProfileJpaEntity existing,
        AuthenticatedUserProfile profile
    ) {
        Long previousGithubId = existing.getGithubId();
        List<String> primaryLanguages = existing.getPrimaryLanguages();
        List<String> topics = existing.getTopics();
        boolean profileReady = existing.isProfileReady();
        String vectorUpdatedAt = existing.getVectorUpdatedAt();
        String profileEmbedding = loadProfileEmbeddingLiteral(previousGithubId);

        releaseLogin(existing, previousGithubId);

        UserProfileJpaEntity canonical = new UserProfileJpaEntity(
            profile.githubId(),
            profile.login(),
            profile.name(),
            profile.email(),
            profile.avatarUrl(),
            primaryLanguages,
            topics,
            profileReady,
            vectorUpdatedAt == null ? INITIAL_VECTOR_UPDATED_AT : vectorUpdatedAt
        );
        repository.saveAndFlush(canonical);
        if (profileEmbedding != null) {
            updateProfileEmbedding(profile.githubId(), profileEmbedding);
        }
        migrateOwnedRows(previousGithubId, profile.githubId());
        repository.delete(existing);
        repository.flush();
        return canonical;
    }

    private void retireConflictingLoginOwner(String login, long canonicalGithubId) {
        repository.findByLogin(login)
            .filter(existing -> existing.getGithubId() != canonicalGithubId)
            .ifPresent(existing -> {
                Long previousGithubId = existing.getGithubId();
                releaseLogin(existing, previousGithubId);
                migrateOwnedRows(previousGithubId, canonicalGithubId);
                repository.delete(existing);
                repository.flush();
            });
    }

    private void releaseLogin(UserProfileJpaEntity existing, long previousGithubId) {
        // OAuth 이전의 synthetic/default 프로필이 같은 login을 점유하면 실제 GitHub id가 login을 갖도록 비웁니다.
        existing.updateIdentity(
            replacedLogin(existing.getLogin(), previousGithubId),
            existing.getName(),
            existing.getEmail(),
            existing.getAvatarUrl()
        );
        repository.saveAndFlush(existing);
    }

    private void migrateOwnedRows(long previousGithubId, long canonicalGithubId) {
        jdbcTemplate.update(
            "UPDATE refresh_token_sessions SET github_id = ? WHERE github_id = ?",
            canonicalGithubId,
            previousGithubId
        );
        jdbcTemplate.update(
            "UPDATE pr_drafts SET owner_github_id = ? WHERE owner_github_id = ?",
            canonicalGithubId,
            previousGithubId
        );
        jdbcTemplate.update(
            "UPDATE issue_guide_generation_requests SET user_github_id = ? WHERE user_github_id = ?",
            canonicalGithubId,
            previousGithubId
        );
    }

    @Override
    public UserProfile refreshCurrentUserProfileVector() {
        ProfileRefreshSeed seed = executeInReadTransaction(() -> {
            UserProfileJpaEntity entity = loadCurrentEntity();
            return new ProfileRefreshSeed(
                entity.getGithubId(),
                entity.getLogin(),
                entity.getName(),
                entity.getPrimaryLanguages(),
                entity.getTopics()
            );
        });

        List<String> resolvedLanguages = seed.primaryLanguages();
        List<String> resolvedTopics = seed.topics();
        if (userContextEnabled) {
            GitHubApiClient.GitHubContributionContext context = gitHubApiClient.loadContributionContext(seed.login());
            resolvedLanguages = nonEmptyOrCurrent(context.primaryLanguages(), resolvedLanguages);
            resolvedTopics = nonEmptyOrCurrent(context.topics(), resolvedTopics);
        }

        String refreshedAt = Instant.now().toString();
        String embeddingInput = buildProfileEmbeddingInput(seed.login(), seed.name(), resolvedLanguages, resolvedTopics);
        String profileEmbedding = createProfileEmbedding(embeddingInput);
        final List<String> nextLanguages = resolvedLanguages;
        final List<String> nextTopics = resolvedTopics;

        return executeInWriteTransaction(() -> {
            UserProfileJpaEntity fresh = loadCurrentEntity();
            if (userContextEnabled) {
                fresh.refreshVector(refreshedAt, nextLanguages, nextTopics);
            } else {
                fresh.refreshVector(refreshedAt);
            }
            repository.saveAndFlush(fresh);
            // 일시적 embedding 실패로 기존 유효 벡터를 NULL로 덮어쓰지 않습니다.
            if (profileEmbedding != null) {
                updateProfileEmbedding(fresh.getGithubId(), profileEmbedding);
            }
            return UserProfilePersistenceMapper.toDomain(fresh);
        });
    }

    @Override
    public EmbeddingBackfillResult backfillMissingProfileEmbeddings(int limit) {
        int requestedLimit = normalizeLimit(limit);
        ProfileBackfillSnapshot snapshot = executeInReadTransaction(() -> new ProfileBackfillSnapshot(
            repository.findByProfileEmbeddingIsNullOrderByGithubIdAsc(PageRequest.of(0, requestedLimit)),
            saturatedInt(repository.countByProfileEmbeddingIsNotNull())
        ));
        List<UserProfileJpaEntity> candidates = snapshot.candidates();
        int skippedExisting = snapshot.skippedExisting();

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

        int skippedBlankText = 0;
        int skippedDimensionMismatch = 0;
        int failed = 0;
        List<String> failureReasons = new ArrayList<>();
        List<ProfileEmbeddingUpdate> updates = new ArrayList<>(candidates.size());

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
                updates.add(new ProfileEmbeddingUpdate(entity.getGithubId(), vector.toPgvectorLiteral()));
            } catch (RuntimeException exception) {
                failed++;
                addFailureReason(failureReasons, profileLabel(entity) + ": " + exception.getClass().getSimpleName());
            }
        }

        if (!updates.isEmpty()) {
            executeInWriteTransaction(() -> batchUpdateProfileEmbeddings(updates));
        }

        return new EmbeddingBackfillResult(
            "profiles",
            requestedLimit,
            candidates.size(),
            updates.size(),
            skippedExisting,
            skippedBlankText,
            0,
            skippedDimensionMismatch,
            failed,
            failureReasons
        );
    }

    private <T> T executeInReadTransaction(Supplier<T> action) {
        if (readTransactionTemplate == null) {
            return action.get();
        }
        return readTransactionTemplate.execute(status -> action.get());
    }

    private <T> T executeInWriteTransaction(Supplier<T> action) {
        if (writeTransactionTemplate == null) {
            return action.get();
        }
        return writeTransactionTemplate.execute(status -> action.get());
    }

    private void executeInWriteTransaction(Runnable action) {
        if (writeTransactionTemplate == null) {
            action.run();
            return;
        }
        writeTransactionTemplate.executeWithoutResult(status -> action.run());
    }

    private String createProfileEmbedding(String input) {
        EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            return null;
        }

        try {
            EmbeddingVector vector = embeddingClient.embed(input);
            if (vector.dimension() != expectedEmbeddingDimensions) {
                return null;
            }
            return vector.toPgvectorLiteral();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String loadProfileEmbeddingLiteral(long githubId) {
        return jdbcTemplate.query(
            "SELECT CAST(profile_embedding AS VARCHAR) FROM user_profiles WHERE github_id = ?",
            preparedStatement -> preparedStatement.setLong(1, githubId),
            resultSet -> resultSet.next() ? resultSet.getString(1) : null
        );
    }

    private void batchUpdateProfileEmbeddings(List<ProfileEmbeddingUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        String sql = profileEmbeddingColumnUsesPgvector()
            ? "UPDATE user_profiles SET profile_embedding = CAST(? AS vector) WHERE github_id = ?"
            : "UPDATE user_profiles SET profile_embedding = ? WHERE github_id = ?";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                ProfileEmbeddingUpdate update = updates.get(index);
                ps.setString(1, update.profileEmbedding());
                ps.setLong(2, update.githubId());
            }

            @Override
            public int getBatchSize() {
                return updates.size();
            }
        });
    }

    private void updateProfileEmbedding(long githubId, String profileEmbedding) {
        if (profileEmbedding == null) {
            return;
        }

        if (profileEmbeddingColumnUsesPgvector()) {
            jdbcTemplate.update(
                "UPDATE user_profiles SET profile_embedding = CAST(? AS vector) WHERE github_id = ?",
                profileEmbedding,
                githubId
            );
            return;
        }

        jdbcTemplate.update(
            "UPDATE user_profiles SET profile_embedding = ? WHERE github_id = ?",
            profileEmbedding,
            githubId
        );
    }

    private boolean profileEmbeddingColumnUsesPgvector() {
        if (pgvectorProfileEmbeddingColumn != null) {
            return pgvectorProfileEmbeddingColumn;
        }

        try {
            Boolean result = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
                FROM information_schema.columns
                WHERE table_schema = 'public'
                    AND table_name = 'user_profiles'
                    AND column_name = 'profile_embedding'
                    AND udt_name = 'vector'
                """,
                Boolean.class
            );
            pgvectorProfileEmbeddingColumn = Boolean.TRUE.equals(result);
        } catch (DataAccessException exception) {
            pgvectorProfileEmbeddingColumn = false;
        }
        return pgvectorProfileEmbeddingColumn;
    }

    private static String buildProfileEmbeddingInput(UserProfileJpaEntity entity) {
        return buildProfileEmbeddingInput(
            entity.getLogin(),
            entity.getName(),
            entity.getPrimaryLanguages(),
            entity.getTopics()
        );
    }

    private static String buildProfileEmbeddingInput(
        String login,
        String name,
        List<String> primaryLanguages,
        List<String> topics
    ) {
        return String.join("\n",
            "Login: " + login,
            "Name: " + name,
            "Primary languages: " + String.join(", ", primaryLanguages),
            "Topics: " + String.join(", ", topics)
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

    private static String replacedLogin(String login, long previousGithubId) {
        String suffix = "-replaced-" + previousGithubId;
        int maxLoginLength = 255;
        int prefixLength = Math.min(login.length(), maxLoginLength - suffix.length());
        return login.substring(0, prefixLength) + suffix;
    }

    private static void addFailureReason(List<String> failureReasons, String reason) {
        if (failureReasons.size() < MAX_FAILURE_REASON_SAMPLES) {
            failureReasons.add(reason);
        }
    }

    private record ProfileRefreshSeed(
        long githubId,
        String login,
        String name,
        List<String> primaryLanguages,
        List<String> topics
    ) {
    }

    private record ProfileEmbeddingUpdate(long githubId, String profileEmbedding) {
    }

    private record ProfileBackfillSnapshot(
        List<UserProfileJpaEntity> candidates,
        int skippedExisting
    ) {
    }
}
