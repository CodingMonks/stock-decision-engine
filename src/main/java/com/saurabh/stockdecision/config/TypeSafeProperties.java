package com.saurabh.stockdecision.config;

import com.saurabh.stockdecision.security.SecretRedactor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Connection settings for Jev (TypeSafe AI). The evaluation is optional: when
 * {@code apiKey} is blank the service makes no external calls.
 */
@ConfigurationProperties(prefix = "typesafe")
public record TypeSafeProperties(
        String apiKey,
        String baseUrl,
        String model,
        Duration timeout
) {

    public boolean enabled() {
        return StringUtils.hasText(apiKey);
    }

    /** Never prints the API key. */
    @Override
    public String toString() {
        return "TypeSafeProperties[apiKey=%s, baseUrl=%s, model=%s, timeout=%s]"
                .formatted(enabled() ? SecretRedactor.MASK : "<unset>", baseUrl, model, timeout);
    }
}
