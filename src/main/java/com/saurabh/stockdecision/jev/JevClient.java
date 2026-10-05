package com.saurabh.stockdecision.jev;

import com.saurabh.stockdecision.config.DecisionProperties;
import com.saurabh.stockdecision.model.Decision;
import com.saurabh.stockdecision.model.DecisionResponse;
import com.saurabh.stockdecision.model.JevError;
import com.saurabh.stockdecision.model.JevEvaluation;
import com.saurabh.stockdecision.security.SecretRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeAnswerTypeException;
import org.springaicommunity.typesafe.exception.TypeSafeApiException;
import org.springaicommunity.typesafe.exception.TypeSafeApiResponseValidationException;
import org.springaicommunity.typesafe.exception.TypeSafeApiTimeoutException;
import org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException;
import org.springaicommunity.typesafe.exception.TypeSafeMissingAnswerException;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Asks Jev (TypeSafe AI) for a structured judgment of a rules-engine decision:
 * a soundness {@link Score}, a reason-consistency {@link Noul} and an independent
 * BUY / SELL / HOLD {@link Choice}, all in one call.
 *
 * <p>Failures never break the decision: any Jev error yields a {@link JevResult.Failure}
 * describing the cause.
 */
public class JevClient {

    private static final Logger log = LoggerFactory.getLogger(JevClient.class);

    static final String SOUNDNESS = "soundness";
    static final String REASON_CONSISTENT = "reason_consistent";
    static final String JEV_DECISION = "jev_decision";

    static final Map<String, Question> QUESTIONS = Map.of(
            SOUNDNESS, Score.builder()
                    .instructions("Given only this position's average purchase price, current price, change "
                            + "and the investor's take-profit / stop-loss / average-down thresholds, how sound "
                            + "is the recommended action?")
                    .level("Unsound: the action contradicts the position data or common risk discipline")
                    .level("Questionable: defensible, but a reasonable investor would likely act differently")
                    .level("Reasonable: a sensible action with minor caveats")
                    .level("Sound: clearly the right action for this position and these thresholds")
                    .build(),
            REASON_CONSISTENT, Noul.builder()
                    .instructions("Is the stated reason consistent with the prices, the change percent "
                            + "and the thresholds?")
                    .whenTrue("Every number and threshold cited in the reason matches the position data")
                    .whenFalse("The reason cites a wrong number, a wrong threshold, or a rule that does not apply")
                    .build(),
            JEV_DECISION, Choice.builder()
                    .instructions("Considering only this position and these thresholds, which action "
                            + "would you take?")
                    .option(Decision.BUY.name(), "Add to the position, e.g. average down at a moderate dip")
                    .option(Decision.SELL.name(), "Exit the position, e.g. lock in a target gain or cut a large loss")
                    .option(Decision.HOLD.name(), "Keep the position unchanged")
                    .build());

    private final TypeSafeClient client;
    private final DecisionProperties thresholds;
    private final String endpoint;
    private final SecretRedactor redactor;

    public JevClient(TypeSafeClient client, DecisionProperties thresholds, String endpoint, SecretRedactor redactor) {
        this.client = client;
        this.thresholds = thresholds;
        this.endpoint = redactor.redact(endpoint);
        this.redactor = redactor;
    }

    public JevResult evaluate(DecisionResponse decision) {
        Map<String, Object> state = state(decision);
        log.info("Jev evaluate: stock={} action={} model={} url={}",
                decision.stockName(), decision.decision(), client.defaultModel(), endpoint);
        log.debug("Jev evaluate: state={}", state);

        long start = System.nanoTime();
        try {
            SystemOneResponse response = client.systemOne(state, QUESTIONS);
            JevEvaluation evaluation = toEvaluation(decision.decision(), response);
            logSuccess(decision.stockName(), response, evaluation, elapsedMs(start));
            return new JevResult.Success(evaluation);
        } catch (RuntimeException e) {
            JevError error = redact(toError(e));
            logFailure(decision.stockName(), error, e, elapsedMs(start));
            return new JevResult.Failure(error);
        }
    }

    private Map<String, Object> state(DecisionResponse d) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("stock", d.stockName());
        state.put("averagePurchasePrice", d.averagePurchasePrice());
        state.put("currentPrice", d.currentPrice());
        state.put("changePercent", d.changePercent());
        state.put("thresholds", Map.of(
                "takeProfitPercent", thresholds.takeProfitPercent(),
                "stopLossPercent", thresholds.stopLossPercent(),
                "averageDownPercent", thresholds.averageDownPercent()));
        state.put("recommendedAction", d.decision().name());
        state.put("reason", d.reason());
        return state;
    }

    private static JevEvaluation toEvaluation(Decision rulesDecision, SystemOneResponse response) {
        ScoreAnswer soundness = response.score(SOUNDNESS);
        ChoiceAnswer pick = response.choice(JEV_DECISION);
        Decision jevDecision = Decision.valueOf(pick.value().toUpperCase(Locale.ROOT));

        return new JevEvaluation(
                response.model(),
                new JevEvaluation.Soundness(soundness.value(), soundness.maxLevel(),
                        soundness.nearestLabel(), soundness.confidence()),
                response.noulValue(REASON_CONSISTENT),
                new JevEvaluation.JevDecision(jevDecision, pick.probabilities(), pick.confidence()),
                jevDecision == rulesDecision);
    }

    private JevError redact(JevError error) {
        return new JevError(error.code(), redactor.redact(error.message()), error.httpStatus(), error.requestId());
    }

    static JevError toError(RuntimeException e) {
        if (e instanceof TypeSafeApiException api) {
            String message = api.errorMessage() != null ? api.errorMessage() : api.getMessage();
            return new JevError(codeFor(api), message, api.status() > 0 ? api.status() : null, api.requestId());
        }
        if (e instanceof TypeSafeApiTimeoutException timeout) {
            return new JevError(JevError.Code.TIMEOUT, "No answer from Jev within " + timeout.timeout(), null, null);
        }
        if (e instanceof TypeSafeApiConnectionException) {
            return new JevError(JevError.Code.CONNECTION, "Could not reach Jev: " + e.getMessage(), null, null);
        }
        if (e instanceof TypeSafeMissingAnswerException || e instanceof TypeSafeAnswerTypeException
                || e instanceof IllegalArgumentException) {
            return new JevError(JevError.Code.INVALID_RESPONSE, "Unexpected Jev answer: " + e.getMessage(), null, null);
        }
        return new JevError(JevError.Code.UNKNOWN, e.getClass().getSimpleName() + ": " + e.getMessage(), null, null);
    }

    private static JevError.Code codeFor(TypeSafeApiException e) {
        if (e instanceof TypeSafeApiResponseValidationException) {
            return JevError.Code.INVALID_RESPONSE;
        }
        return switch (e.status()) {
            case 400, 422 -> JevError.Code.BAD_REQUEST;
            case 401 -> JevError.Code.AUTHENTICATION;
            case 403 -> JevError.Code.PERMISSION_DENIED;
            case 404 -> JevError.Code.NOT_FOUND;
            case 429 -> JevError.Code.RATE_LIMITED;
            default -> e.status() >= 500 ? JevError.Code.SERVER_ERROR : JevError.Code.UNKNOWN;
        };
    }

    private static void logSuccess(String stock, SystemOneResponse response, JevEvaluation e, long ms) {
        log.info("Jev result: stock={} requestId={} model={} soundness={} ({}, confidence={}) "
                        + "reasonConsistency={} jevDecision={} (confidence={}) agreesWithRules={} tokens={} in {} ms",
                stock, response.requestId(), response.model(),
                round(e.soundness().score()), e.soundness().label(), round(e.soundness().confidence()),
                round(e.reasonConsistency()), e.jevDecision().decision(), round(e.jevDecision().confidence()),
                e.agreesWithRules(), response.usage() != null ? response.usage().totalTokens() : null, ms);
        log.debug("Jev result: answers={}", response.answers());
    }

    private void logFailure(String stock, JevError error, RuntimeException e, long ms) {
        String format = "Jev failed: stock={} code={} httpStatus={} requestId={} message=\"{}\" after {} ms; "
                + "falling back to basic calculation";
        Object[] args = {stock, error.code(), error.httpStatus(), error.requestId(), error.message(), ms};
        if (error.code().isConfigurationProblem() || error.code() == JevError.Code.UNKNOWN) {
            log.error(format, args);
        } else {
            log.warn(format, args);
        }
        if (log.isDebugEnabled()) {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            log.debug("Jev failure stack trace: {}", redactor.redact(trace.toString()));
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
