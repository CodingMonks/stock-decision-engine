package com.saurabh.stockdecision.security;

import com.saurabh.stockdecision.config.TypeSafeProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRedactorTest {

    private final SecretRedactor redactor = new SecretRedactor(Arrays.asList("ts-secret-key-123", "app-key-456", "", null, "abc"));

    @Test
    void masksKnownSecrets() {
        assertThat(redactor.redact("key=ts-secret-key-123 other=app-key-456"))
                .isEqualTo("key=**** other=****");
    }

    @Test
    void masksBearerTokens() {
        assertThat(redactor.redact("Authorization: Bearer some.unknown-token")).isEqualTo("Authorization: Bearer ****");
    }

    @Test
    void ignoresBlankAndTooShortSecrets() {
        assertThat(redactor.redact("abc is fine")).isEqualTo("abc is fine");
        assertThat(redactor.redact(null)).isNull();
    }

    @Test
    void typeSafePropertiesNeverPrintTheKey() {
        TypeSafeProperties props = new TypeSafeProperties("ts-secret-key-123", "https://api.typesafe.ai",
                "jev-latest", Duration.ofSeconds(5));

        assertThat(props.toString()).doesNotContain("ts-secret-key-123").contains("apiKey=****");
        assertThat(new TypeSafeProperties("", "u", "m", null).toString()).contains("apiKey=<unset>");
        assertThat(List.of(props).toString()).doesNotContain("ts-secret-key-123");
    }
}
