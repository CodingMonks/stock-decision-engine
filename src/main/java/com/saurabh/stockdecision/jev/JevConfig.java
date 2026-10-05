package com.saurabh.stockdecision.jev;

import com.saurabh.stockdecision.config.DecisionProperties;
import com.saurabh.stockdecision.config.TypeSafeProperties;
import com.saurabh.stockdecision.security.SecretRedactor;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Creates {@link JevClient} only when {@code TYPESAFE_API_KEY} is set.
 */
@Configuration
@ConditionalOnExpression("'${typesafe.api-key:}' != ''")
public class JevConfig {

    static final String SYSTEM_ONE_PATH = "/v1/systemone";

    /** Masks both this service's API key and the TypeSafe key in anything Jev-related that is logged or returned. */
    @Bean
    public SecretRedactor jevSecretRedactor(TypeSafeProperties props, @Value("${app.api-key:}") String appApiKey) {
        return new SecretRedactor(List.of(props.apiKey(), appApiKey));
    }

    @Bean
    public TypeSafeClient typeSafeClient(TypeSafeProperties props, SecretRedactor jevSecretRedactor) {
        return TypeSafeClient.builder()
                .apiKey(props.apiKey())
                .baseUrl(props.baseUrl())
                .defaultModel(props.model())
                .timeout(props.timeout())
                .restClientBuilder(RestClient.builder().requestInterceptor(new JevHttpLogger(jevSecretRedactor)))
                .build();
    }

    @Bean
    public JevClient jevClient(TypeSafeClient typeSafeClient, DecisionProperties thresholds, TypeSafeProperties props,
                               SecretRedactor jevSecretRedactor) {
        return new JevClient(typeSafeClient, thresholds, props.baseUrl() + SYSTEM_ONE_PATH, jevSecretRedactor);
    }
}
