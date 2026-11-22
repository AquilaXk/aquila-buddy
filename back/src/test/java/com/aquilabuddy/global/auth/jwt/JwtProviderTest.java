package com.aquilabuddy.global.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "local-development-jwt-secret-32bytes-minimum-value";

    @Test
    void generatesValidTokenWithGithubIdSubject() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));

        String token = provider.generateToken("12345");

        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.getGithubId(token)).isEqualTo("12345");
    }

    @Test
    void rejectsMalformedToken() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));

        assertThat(provider.isValid("not-a-jwt")).isFalse();
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        JwtProperties shortProperties = new JwtProperties("too-short-31-bytes-secret-value", Duration.ofHours(1));

        assertThatThrownBy(() -> new JwtProvider(shortProperties))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNoneAlgorithmTokenEvenWhenSigned() throws Exception {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));
        String noneHeader = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        long exp = Instant.now().plusSeconds(3600).getEpochSecond();
        String payload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(("{\"sub\":\"12345\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));

        String unsignedToken = noneHeader + "." + payload + ".";
        assertThat(provider.isValid(unsignedToken)).isFalse();
        assertThatThrownBy(() -> provider.getGithubId(unsignedToken))
            .isInstanceOf(IllegalArgumentException.class);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signingInput = noneHeader + "." + payload;
        String forgedSig = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        String forgedNoneToken = signingInput + "." + forgedSig;

        assertThat(provider.isValid(forgedNoneToken)).isFalse();
        assertThatThrownBy(() -> provider.getGithubId(forgedNoneToken))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTamperedPayloadSubjectAndSingleBitSignatureFlip() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));
        String token = provider.generateToken("12345");
        String[] parts = token.split("\\.");

        String decodedPayload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(decodedPayload.replace("12345", "12346").getBytes(StandardCharsets.UTF_8));
        String payloadTamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(provider.isValid(payloadTamperedToken)).isFalse();
        assertThatThrownBy(() -> provider.getGithubId(payloadTamperedToken))
            .isInstanceOf(IllegalArgumentException.class);

        byte[] sigBytes = Base64.getUrlDecoder().decode(parts[2]);
        sigBytes[0] ^= 0x01;
        String tamperedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        String signatureTamperedToken = parts[0] + "." + parts[1] + "." + tamperedSig;

        assertThat(provider.isValid(signatureTamperedToken)).isFalse();
        assertThatThrownBy(() -> provider.getGithubId(signatureTamperedToken))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtProvider issuer = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));
        JwtProvider verifier = new JwtProvider(
            new JwtProperties("another-development-jwt-secret-32bytes-minimum", Duration.ofHours(1))
        );
        String token = issuer.generateToken("12345");

        assertThat(verifier.isValid(token)).isFalse();
        assertThatThrownBy(() -> verifier.getGithubId(token))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtProvider expiredIssuer = new JwtProvider(new JwtProperties(SECRET, Duration.ofSeconds(-1)));
        String expiredToken = expiredIssuer.generateToken("12345");

        assertThat(expiredIssuer.isValid(expiredToken)).isFalse();
        assertThatThrownBy(() -> expiredIssuer.getGithubId(expiredToken))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOversizedTokenExceeding4096Characters() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));
        String oversizedToken = provider.generateToken("a".repeat(3_100));

        assertThat(oversizedToken.length()).isGreaterThan(4_096);
        assertThat(provider.isValid(oversizedToken)).isFalse();
        assertThatThrownBy(() -> provider.getGithubId(oversizedToken))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesJsonEscapedSpecialCharactersInSubjectRoundTrip() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));
        String specialSubject = "dev\"user\\1";

        String token = provider.generateToken(specialSubject);

        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.getGithubId(token)).isEqualTo(specialSubject);
    }
}
