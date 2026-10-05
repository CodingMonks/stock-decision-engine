package com.saurabh.stockdecision.security;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Masks known secrets and bearer tokens in text that is about to be logged or returned to a caller.
 * Defence in depth: nothing should contain a secret in the first place.
 */
public final class SecretRedactor {

    public static final String MASK = "****";

    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final int MIN_SECRET_LENGTH = 4;

    private final List<String> secrets;

    public SecretRedactor(Collection<String> secrets) {
        this.secrets = secrets.stream()
                .filter(s -> StringUtils.hasText(s) && s.length() >= MIN_SECRET_LENGTH)
                .toList();
    }

    public String redact(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (String secret : secrets) {
            result = result.replace(secret, MASK);
        }
        return BEARER.matcher(result).replaceAll("$1" + MASK);
    }
}
