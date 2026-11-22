package com.aquilabuddy.global.auth.jwt;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * RFC 7519 및 RFC 7518 HS256 표준 명세에 따라 GitHub 사용자 식별자(sub)를 발급/검증하는 JWT 공급자.
 */
@Component
public class JwtProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String EXPECTED_ALG = "HS256";
    private static final String ENCODED_HEADER = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
    private static final int MIN_SECRET_BYTES = 32;
    private static final int HMAC_SHA256_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 4_096;
    private static final int MAX_JSON_DEPTH = 32;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private final SecretKeySpec signingKey;
    private final Duration expiration;
    private final ThreadLocal<Mac> macThreadLocal;

    public JwtProvider(JwtProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("JwtProperties must not be null.");
        }
        String secret = properties.secret();
        if (secret == null) {
            throw new IllegalArgumentException("JWT secret must not be null.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes (256 bits).");
        }
        if (properties.expiration() == null) {
            throw new IllegalArgumentException("JWT expiration must not be null.");
        }

        this.signingKey = new SecretKeySpec(secretBytes, HMAC_ALGORITHM);
        this.expiration = properties.expiration();
        this.macThreadLocal = ThreadLocal.withInitial(this::createInitializedMac);
        // 생성 시점에 알고리즘·키 초기화를 즉시 검증해 fail-closed로 실패합니다.
        this.macThreadLocal.get();
    }

    public String generateToken(String githubId) {
        if (githubId == null || githubId.isBlank()) {
            throw new IllegalArgumentException("githubId must not be blank.");
        }

        Instant issuedAt = Instant.now();
        long iatEpochSeconds = issuedAt.getEpochSecond();
        long expEpochSeconds = issuedAt.plus(expiration).getEpochSecond();

        StringBuilder payloadJson = new StringBuilder(githubId.length() + 48);
        payloadJson.append("{\"sub\":");
        appendJsonString(payloadJson, githubId);
        payloadJson.append(",\"iat\":").append(iatEpochSeconds);
        payloadJson.append(",\"exp\":").append(expEpochSeconds).append('}');

        String encodedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payloadJson.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder tokenBuilder = new StringBuilder(ENCODED_HEADER.length() + encodedPayload.length() + 45);
        tokenBuilder.append(ENCODED_HEADER).append('.').append(encodedPayload);
        byte[] signature = sign(tokenBuilder.toString());
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        tokenBuilder.append('.').append(encodedSignature);
        return tokenBuilder.toString();
    }

    public String getGithubId(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Invalid JWT token.");
        }

        int firstDot = token.indexOf('.');
        if (firstDot <= 0) {
            throw new IllegalArgumentException("Invalid JWT format.");
        }
        int secondDot = token.indexOf('.', firstDot + 1);
        if (secondDot <= firstDot + 1 || secondDot >= token.length() - 1) {
            throw new IllegalArgumentException("Invalid JWT format.");
        }
        if (token.indexOf('.', secondDot + 1) != -1) {
            throw new IllegalArgumentException("Invalid JWT format.");
        }

        String headerPart = token.substring(0, firstDot);
        String payloadPart = token.substring(firstDot + 1, secondDot);
        String signaturePart = token.substring(secondDot + 1);
        String signingInput = token.substring(0, secondDot);

        byte[] actualSignature = Base64.getUrlDecoder().decode(signaturePart);
        byte[] expectedSignature = sign(signingInput);
        if (expectedSignature.length != HMAC_SHA256_BYTES
            || actualSignature.length != HMAC_SHA256_BYTES
            || !MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        if (!ENCODED_HEADER.equals(headerPart)) {
            String headerJson = decodeBase64UrlUtf8(headerPart);
            String alg = new JsonClaimScanner(headerJson).readHeaderAlgorithm();
            if (!EXPECTED_ALG.equals(alg)) {
                throw new IllegalArgumentException("Unsupported JWT algorithm.");
            }
        }

        String payloadJson = decodeBase64UrlUtf8(payloadPart);
        long nowEpochSeconds = Instant.now().getEpochSecond();
        return new JsonClaimScanner(payloadJson).readValidatedSubject(nowEpochSeconds);
    }

    public boolean isValid(String token) {
        try {
            getGithubId(token);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Mac createInitializedMac() {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to initialize HmacSHA256 Mac.", exception);
        }
    }

    private byte[] sign(String signingInput) {
        Mac mac = macThreadLocal.get();
        mac.reset();
        return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64UrlUtf8(String encodedPart) {
        byte[] decoded = Base64.getUrlDecoder().decode(encodedPart);
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded))
                .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 sequence in JWT segment.", exception);
        }
    }

    private static void appendJsonString(StringBuilder out, String value) {
        out.append('"');
        int len = value.length();
        for (int index = 0; index < len; index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append("\\u00")
                            .append(HEX_DIGITS[(ch >>> 4) & 0x0f])
                            .append(HEX_DIGITS[ch & 0x0f]);
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class JsonClaimScanner {

        private final String json;
        private final int len;
        private int pos;

        private JsonClaimScanner(String json) {
            this.json = json;
            this.len = json.length();
        }

        private String readHeaderAlgorithm() {
            String algorithm = null;
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (!peek('}')) {
                while (true) {
                    String key = parseString(true);
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    if ("alg".equals(key)) {
                        if (algorithm != null) {
                            throw new IllegalArgumentException("Duplicate alg header.");
                        }
                        algorithm = parseString(true);
                    } else {
                        skipValue(0);
                    }
                    skipWhitespace();
                    if (consume(',')) {
                        skipWhitespace();
                        continue;
                    }
                    break;
                }
            }
            expect('}');
            skipWhitespace();
            if (pos != len) {
                throw new IllegalArgumentException("Trailing content in JWT header.");
            }
            if (algorithm == null) {
                throw new IllegalArgumentException("Missing alg header.");
            }
            return algorithm;
        }

        private String readValidatedSubject(long nowEpochSeconds) {
            String subject = null;
            boolean hasExp = false;
            boolean expired = false;

            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (!peek('}')) {
                while (true) {
                    String key = parseString(true);
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    if ("sub".equals(key)) {
                        if (subject != null) {
                            throw new IllegalArgumentException("Duplicate sub claim.");
                        }
                        subject = parseString(true);
                    } else if ("exp".equals(key)) {
                        if (hasExp) {
                            throw new IllegalArgumentException("Duplicate exp claim.");
                        }
                        hasExp = true;
                        expired = parseNumericExpiration(nowEpochSeconds);
                    } else {
                        skipValue(0);
                    }
                    skipWhitespace();
                    if (consume(',')) {
                        skipWhitespace();
                        continue;
                    }
                    break;
                }
            }
            expect('}');
            skipWhitespace();
            if (pos != len) {
                throw new IllegalArgumentException("Trailing content in JWT payload.");
            }
            if (!hasExp || expired) {
                throw new IllegalArgumentException("JWT token is expired or missing exp.");
            }
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("JWT sub claim must not be blank.");
            }
            return subject;
        }

        private void skipValue(int depth) {
            if (depth > MAX_JSON_DEPTH) {
                throw new IllegalArgumentException("JSON depth exceeds limit.");
            }
            if (pos >= len) {
                throw new IllegalArgumentException("Unexpected end of JSON.");
            }
            char ch = json.charAt(pos);
            switch (ch) {
                case '"' -> parseString(false);
                case '{' -> skipObject(depth + 1);
                case '[' -> skipArray(depth + 1);
                case 't' -> expectLiteral("true");
                case 'f' -> expectLiteral("false");
                case 'n' -> expectLiteral("null");
                default -> {
                    if (ch == '-' || isAsciiDigit(ch)) {
                        parseNumberToken(false, 0L);
                    } else {
                        throw new IllegalArgumentException("Invalid JSON value.");
                    }
                }
            }
        }

        private void skipObject(int depth) {
            pos++;
            skipWhitespace();
            if (!peek('}')) {
                while (true) {
                    parseString(false);
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    skipValue(depth);
                    skipWhitespace();
                    if (consume(',')) {
                        skipWhitespace();
                        continue;
                    }
                    break;
                }
            }
            expect('}');
        }

        private void skipArray(int depth) {
            pos++;
            skipWhitespace();
            if (!peek(']')) {
                while (true) {
                    skipValue(depth);
                    skipWhitespace();
                    if (consume(',')) {
                        skipWhitespace();
                        continue;
                    }
                    break;
                }
            }
            expect(']');
        }

        private String parseString(boolean capture) {
            expect('"');
            int start = pos;
            StringBuilder unescaped = null;
            while (pos < len) {
                char ch = json.charAt(pos++);
                if (ch == '"') {
                    if (!capture) {
                        return null;
                    }
                    return unescaped == null ? json.substring(start, pos - 1) : unescaped.toString();
                }
                if (ch == '\\') {
                    if (capture && unescaped == null) {
                        unescaped = new StringBuilder(Math.max(16, pos - start + 16));
                        unescaped.append(json, start, pos - 1);
                    }
                    if (pos >= len) {
                        throw new IllegalArgumentException("Unterminated escape in JSON string.");
                    }
                    char esc = json.charAt(pos++);
                    char decoded = switch (esc) {
                        case '"', '\\', '/' -> esc;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> readUnicodeEscape();
                        default -> throw new IllegalArgumentException("Invalid JSON escape sequence.");
                    };
                    if (capture) {
                        unescaped.append(decoded);
                    }
                } else if (ch < 0x20) {
                    throw new IllegalArgumentException("Unescaped control character in JSON string.");
                } else if (capture && unescaped != null) {
                    unescaped.append(ch);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string.");
        }

        private char readUnicodeEscape() {
            if (pos + 4 > len) {
                throw new IllegalArgumentException("Incomplete unicode escape in JSON string.");
            }
            int code = 0;
            for (int index = 0; index < 4; index++) {
                int digit = hexValue(json.charAt(pos++));
                if (digit < 0) {
                    throw new IllegalArgumentException("Invalid hex digit in unicode escape.");
                }
                code = (code << 4) | digit;
            }
            return (char) code;
        }

        private boolean parseNumericExpiration(long nowEpochSeconds) {
            return parseNumberToken(true, nowEpochSeconds);
        }

        private boolean parseNumberToken(boolean evaluateExp, long nowEpochSeconds) {
            int start = pos;
            if (pos < len && json.charAt(pos) == '-') {
                pos++;
            }
            if (pos >= len) {
                throw new IllegalArgumentException("Invalid JSON number.");
            }
            char first = json.charAt(pos++);
            if (first == '0') {
                if (pos < len && isAsciiDigit(json.charAt(pos))) {
                    throw new IllegalArgumentException("Leading zero in JSON number.");
                }
            } else if (first >= '1' && first <= '9') {
                while (pos < len && isAsciiDigit(json.charAt(pos))) {
                    pos++;
                }
            } else {
                throw new IllegalArgumentException("Invalid JSON number.");
            }

            boolean hasFractionOrExponent = false;
            if (pos < len && json.charAt(pos) == '.') {
                hasFractionOrExponent = true;
                pos++;
                if (pos >= len || !isAsciiDigit(json.charAt(pos))) {
                    throw new IllegalArgumentException("Invalid fraction in JSON number.");
                }
                while (pos < len && isAsciiDigit(json.charAt(pos))) {
                    pos++;
                }
            }

            if (pos < len && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
                hasFractionOrExponent = true;
                pos++;
                if (pos < len && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
                    pos++;
                }
                if (pos >= len || !isAsciiDigit(json.charAt(pos))) {
                    throw new IllegalArgumentException("Invalid exponent in JSON number.");
                }
                while (pos < len && isAsciiDigit(json.charAt(pos))) {
                    pos++;
                }
            }

            if (!evaluateExp) {
                return false;
            }
            if (!hasFractionOrExponent) {
                long expEpochSeconds = Long.parseLong(json, start, pos, 10);
                return nowEpochSeconds >= expEpochSeconds;
            }
            double expDouble = Double.parseDouble(json.substring(start, pos));
            if (!Double.isFinite(expDouble)) {
                throw new IllegalArgumentException("Non-finite exp claim.");
            }
            return (double) nowEpochSeconds >= expDouble;
        }

        private void expectLiteral(String literal) {
            int literalLen = literal.length();
            if (pos + literalLen > len || !json.regionMatches(pos, literal, 0, literalLen)) {
                throw new IllegalArgumentException("Invalid JSON literal.");
            }
            pos += literalLen;
        }

        private void skipWhitespace() {
            while (pos < len) {
                char ch = json.charAt(pos);
                if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private boolean peek(char expected) {
            return pos < len && json.charAt(pos) == expected;
        }

        private boolean consume(char expected) {
            if (peek(expected)) {
                pos++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw new IllegalArgumentException("Unexpected character in JSON.");
            }
        }

        private static boolean isAsciiDigit(char ch) {
            return ch >= '0' && ch <= '9';
        }

        private static int hexValue(char ch) {
            if (ch >= '0' && ch <= '9') {
                return ch - '0';
            }
            if (ch >= 'a' && ch <= 'f') {
                return ch - 'a' + 10;
            }
            if (ch >= 'A' && ch <= 'F') {
                return ch - 'A' + 10;
            }
            return -1;
        }
    }
}
