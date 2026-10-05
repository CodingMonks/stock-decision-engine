package com.saurabh.stockdecision.jev;

import com.saurabh.stockdecision.config.DecisionProperties;
import com.saurabh.stockdecision.model.Decision;
import com.saurabh.stockdecision.model.DecisionResponse;
import com.saurabh.stockdecision.model.JevError;
import com.saurabh.stockdecision.model.JevEvaluation;
import com.saurabh.stockdecision.security.SecretRedactor;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException;
import org.springaicommunity.typesafe.exception.TypeSafeApiException;
import org.springaicommunity.typesafe.exception.TypeSafeApiTimeoutException;
import org.springaicommunity.typesafe.exception.TypeSafeAuthenticationException;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JevClientTest {

    private static final String TYPESAFE_KEY = "ts-secret-key-123";

    private final TypeSafeClient typeSafe = mock(TypeSafeClient.class);
    private final JevClient jev = new JevClient(typeSafe,
            new DecisionProperties(new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("5")),
            "https://api.typesafe.ai/v1/systemone", new SecretRedactor(List.of(TYPESAFE_KEY)));

    private final DecisionResponse decision = new DecisionResponse("AAPL", new BigDecimal("150"),
            new BigDecimal("185"), new BigDecimal("23.33"), Decision.SELL,
            "Gain of 23.33% reached the take-profit target of +20%.", Instant.EPOCH, DecisionResponse.POWERED_BY_BASIC, null, null);

    @Test
    void mapsJevAnswersToEvaluation() {
        when(typeSafe.systemOne(anyMap(), eq(JevClient.QUESTIONS))).thenReturn(new SystemOneResponse(
                "jev-1.13.0",
                Map.of(
                        JevClient.SOUNDNESS, new ScoreAnswer(2.6,
                                Map.of(0, JsonContent.of("Unsound"), 1, JsonContent.of("Questionable"),
                                        2, JsonContent.of("Reasonable"), 3, JsonContent.of("Sound")),
                                Map.of(0, 0.01, 1, 0.04, 2, 0.30, 3, 0.65), 0.78),
                        JevClient.REASON_CONSISTENT, new NoulAnswer(0.97),
                        JevClient.JEV_DECISION, new ChoiceAnswer("SELL",
                                Map.of("SELL", 0.81, "HOLD", 0.17, "BUY", 0.02), 0.81)),
                null));

        JevResult result = jev.evaluate(decision);

        assertThat(result).isInstanceOfSatisfying(JevResult.Success.class, success -> {
            JevEvaluation e = success.evaluation();
            assertThat(e.model()).isEqualTo("jev-1.13.0");
            assertThat(e.soundness().score()).isEqualTo(2.6);
            assertThat(e.soundness().maxLevel()).isEqualTo(3);
            assertThat(e.soundness().label()).isEqualTo("Sound");
            assertThat(e.soundness().confidence()).isEqualTo(0.78);
            assertThat(e.reasonConsistency()).isEqualTo(0.97);
            assertThat(e.jevDecision().decision()).isEqualTo(Decision.SELL);
            assertThat(e.jevDecision().confidence()).isEqualTo(0.81);
            assertThat(e.agreesWithRules()).isTrue();
        });
    }

    @Test
    void sendsPositionAndThresholdsAsState() {
        when(typeSafe.systemOne(anyMap(), eq(JevClient.QUESTIONS)))
                .thenThrow(new TypeSafeApiConnectionException("down", null));

        jev.evaluate(decision);

        verify(typeSafe).systemOne(org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(state ->
                state.get("stock").equals("AAPL")
                        && state.get("recommendedAction").equals("SELL")
                        && ((Map<?, ?>) state.get("thresholds")).containsKey("stopLossPercent")),
                eq(JevClient.QUESTIONS));
    }

    @Test
    void reportsConnectionFailure() {
        when(typeSafe.systemOne(anyMap(), eq(JevClient.QUESTIONS)))
                .thenThrow(new TypeSafeApiConnectionException("Connection refused", null));

        assertThat(jev.evaluate(decision)).isEqualTo(new JevResult.Failure(new JevError(
                JevError.Code.CONNECTION, "Could not reach Jev: Connection refused", null, null)));
    }

    @Test
    void reportsHttpErrorWithStatusAndRequestId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TypeSafeApiException.REQUEST_ID_HEADER, "req-123");
        when(typeSafe.systemOne(anyMap(), eq(JevClient.QUESTIONS))).thenThrow(new TypeSafeAuthenticationException(
                "401 Unauthorized", 401, "{\"error\":{\"type\":\"authentication_error\",\"message\":\"Invalid API key\"}}",
                headers, "/v1/systemone"));

        JevResult result = jev.evaluate(decision);

        assertThat(result).isInstanceOfSatisfying(JevResult.Failure.class, failure -> {
            assertThat(failure.error().code()).isEqualTo(JevError.Code.AUTHENTICATION);
            assertThat(failure.error().httpStatus()).isEqualTo(401);
            assertThat(failure.error().requestId()).isEqualTo("req-123");
            assertThat(failure.error().message()).isNotBlank();
        });
    }

    @Test
    void redactsSecretsFromErrorMessage() {
        when(typeSafe.systemOne(anyMap(), eq(JevClient.QUESTIONS)))
                .thenThrow(new IllegalStateException("bad header Authorization: Bearer " + TYPESAFE_KEY));

        assertThat(jev.evaluate(decision)).isInstanceOfSatisfying(JevResult.Failure.class, failure -> {
            assertThat(failure.error().message()).doesNotContain(TYPESAFE_KEY);
            assertThat(failure.error().message()).contains(SecretRedactor.MASK);
        });
    }

    @Test
    void reportsTimeout() {
        when(typeSafe.systemOne(anyMap(), eq(JevClient.QUESTIONS)))
                .thenThrow(new TypeSafeApiTimeoutException("timed out", Duration.ofSeconds(5), null));

        assertThat(jev.evaluate(decision)).isInstanceOfSatisfying(JevResult.Failure.class,
                failure -> assertThat(failure.error().code()).isEqualTo(JevError.Code.TIMEOUT));
    }

    @Test
    void reportsUnexpectedAnswer() {
        when(typeSafe.systemOne(anyMap(), eq(JevClient.QUESTIONS))).thenReturn(new SystemOneResponse(
                "jev-latest", Map.of(), null));

        assertThat(jev.evaluate(decision)).isInstanceOfSatisfying(JevResult.Failure.class,
                failure -> assertThat(failure.error().code()).isEqualTo(JevError.Code.INVALID_RESPONSE));
    }
}
