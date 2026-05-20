package com.aquilabuddy.global.auth.refresh;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenSessionJpaRepository repository;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    @Autowired
    public RefreshTokenSessionService(
        RefreshTokenSessionJpaRepository repository,
        RefreshTokenProperties properties
    ) {
        this(repository, properties, Clock.systemUTC());
    }

    RefreshTokenSessionService(
        RefreshTokenSessionJpaRepository repository,
        RefreshTokenProperties properties,
        Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RefreshTokenSession issue(String githubId) {
        Instant now = Instant.now(clock);
        String refreshToken = newToken();
        String tokenHash = hash(refreshToken);
        repository.save(new RefreshTokenSessionJpaEntity(
            tokenHash,
            newFamilyId(),
            Long.parseLong(githubId),
            now,
            now.plus(properties.expiration())
        ));
        return new RefreshTokenSession(githubId, refreshToken);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshTokenSession rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("refresh token이 없습니다.");
        }

        String tokenHash = hash(refreshToken);
        RefreshTokenSessionJpaEntity current = repository.findById(tokenHash)
            .orElseThrow(() -> new InvalidRefreshTokenException("refresh token이 유효하지 않습니다."));
        Instant now = Instant.now(clock);
        if (!current.isUsable(now)) {
            if (current.isReusedOrRevoked()) {
                revokeFamily(current.getFamilyId(), now);
            }
            throw new InvalidRefreshTokenException("refresh token이 유효하지 않습니다.");
        }

        String nextRefreshToken = newToken();
        String nextTokenHash = hash(nextRefreshToken);
        current.rotate(nextTokenHash, now);
        repository.save(current);
        repository.save(new RefreshTokenSessionJpaEntity(
            nextTokenHash,
            current.getFamilyId(),
            current.getGithubId(),
            now,
            now.plus(properties.expiration())
        ));
        return new RefreshTokenSession(String.valueOf(current.getGithubId()), nextRefreshToken);
    }

    @Transactional
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        repository.findById(hash(refreshToken))
            .ifPresent(session -> session.revoke(Instant.now(clock)));
    }

    private void revokeFamily(String familyId, Instant now) {
        var sessions = repository.findAllByFamilyId(familyId);
        sessions.forEach(session -> session.revoke(now));
        repository.saveAll(sessions);
    }

    private static String newToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private static String newFamilyId() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 hash를 사용할 수 없습니다.", exception);
        }
    }
}
