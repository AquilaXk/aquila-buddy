package com.aquilabuddy.global.auth.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Chrome Extension OAuth PKCE 1회용 교환 코드를 발급·검증합니다.
 */
@Service
public class ExtensionOAuthExchangeService {

    static final int MAX_ENTRIES = 1024;
    static final Duration CODE_TTL = Duration.ofSeconds(60);
    private static final int MAX_CODE_LENGTH = 128;
    private static final int MAX_VERIFIER_LENGTH = 512;
    private static final Pattern CODE_CHALLENGE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43,128}$");
    private static final Pattern CODE_VERIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9._~-]{43,128}$");
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ConcurrentHashMap<String, PendingExchange> pendingExchanges = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    @Autowired
    public ExtensionOAuthExchangeService() {
        this(Clock.systemUTC());
    }

    ExtensionOAuthExchangeService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    public static boolean isValidCodeChallenge(String codeChallenge) {
        return codeChallenge != null && CODE_CHALLENGE_PATTERN.matcher(codeChallenge).matches();
    }

    public String issueCode(String githubId, String codeChallenge) {
        if (githubId == null || githubId.isBlank()) {
            throw new IllegalArgumentException("githubId must not be blank.");
        }
        if (!isValidCodeChallenge(codeChallenge)) {
            throw new IllegalArgumentException("유효한 PKCE code_challenge가 필요합니다.");
        }

        Instant now = clock.instant();
        if (pendingExchanges.size() >= MAX_ENTRIES) {
            purgeExpired(now);
        }
        if (pendingExchanges.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("대기 중인 extension OAuth 교환 코드 상한을 초과했습니다.");
        }

        String code = generateOneTimeCode();
        pendingExchanges.put(code, new PendingExchange(githubId.strip(), codeChallenge, now.plus(CODE_TTL)));
        return code;
    }

    public String consumeAndVerify(String code, String codeVerifier) {
        if (code == null || code.isBlank() || code.length() > MAX_CODE_LENGTH) {
            throw new InvalidExtensionOAuthCodeException("유효하지 않은 extension 인증 코드입니다.");
        }
        if (codeVerifier == null || codeVerifier.isBlank() || codeVerifier.length() > MAX_VERIFIER_LENGTH) {
            throw new InvalidExtensionOAuthCodeException("유효하지 않은 PKCE codeVerifier입니다.");
        }

        PendingExchange pending = pendingExchanges.remove(code);
        if (pending == null) {
            throw new InvalidExtensionOAuthCodeException("유효하지 않거나 이미 사용된 인증 코드입니다.");
        }

        if (clock.instant().isAfter(pending.expiresAt())) {
            throw new InvalidExtensionOAuthCodeException("만료된 인증 코드입니다.");
        }

        if (!CODE_VERIFIER_PATTERN.matcher(codeVerifier).matches()) {
            throw new InvalidExtensionOAuthCodeException("PKCE codeVerifier 검증에 실패했습니다.");
        }

        String computedChallenge = computeS256Challenge(codeVerifier);
        boolean matches = MessageDigest.isEqual(
            computedChallenge.getBytes(StandardCharsets.US_ASCII),
            pending.codeChallenge().getBytes(StandardCharsets.US_ASCII)
        );
        if (!matches) {
            throw new InvalidExtensionOAuthCodeException("PKCE codeVerifier 검증에 실패했습니다.");
        }

        return pending.githubId();
    }

    private void purgeExpired(Instant now) {
        pendingExchanges.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private String generateOneTimeCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String computeS256Challenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return URL_ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private record PendingExchange(String githubId, String codeChallenge, Instant expiresAt) {
    }

    public static class InvalidExtensionOAuthCodeException extends RuntimeException {

        public InvalidExtensionOAuthCodeException(String message) {
            super(message);
        }
    }
}
