package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.global.auth.oauth.ExtensionOAuthExchangeService.InvalidExtensionOAuthCodeException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ExtensionOAuthExchangeServiceTest {

    @Test
    void rejectsExpiredAndReusedCodesAndBoundsCapacity() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-21T00:00:00Z"));
        ExtensionOAuthExchangeService service = new ExtensionOAuthExchangeService(clock);
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = s256(verifier);

        String code = service.issueCode("10001", challenge);
        assertThat(service.consumeAndVerify(code, verifier)).isEqualTo("10001");
        assertThatThrownBy(() -> service.consumeAndVerify(code, verifier))
            .isInstanceOf(InvalidExtensionOAuthCodeException.class);

        String expiringCode = service.issueCode("10001", challenge);
        clock.advance(Duration.ofSeconds(61));
        assertThatThrownBy(() -> service.consumeAndVerify(expiringCode, verifier))
            .isInstanceOf(InvalidExtensionOAuthCodeException.class);

        for (int index = 0; index < ExtensionOAuthExchangeService.MAX_ENTRIES; index++) {
            service.issueCode("10001", challenge);
        }
        assertThatThrownBy(() -> service.issueCode("10001", challenge))
            .isInstanceOf(IllegalStateException.class);

        clock.advance(Duration.ofSeconds(61));
        assertThat(service.issueCode("10001", challenge)).isNotBlank();
    }

    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        private void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
