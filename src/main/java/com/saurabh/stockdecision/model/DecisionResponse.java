package com.saurabh.stockdecision.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param poweredBy     {@link #POWERED_BY_AI} when Jev evaluated the decision, otherwise {@link #POWERED_BY_BASIC}
 * @param jevEvaluation Jev's judgment of the decision; null when Jev is not configured or failed
 * @param jevError      why Jev failed; null when Jev succeeded or is not configured
 */
public record DecisionResponse(
        String stockName,
        BigDecimal averagePurchasePrice,
        BigDecimal currentPrice,
        BigDecimal changePercent,
        Decision decision,
        String reason,
        Instant evaluatedAt,
        String poweredBy,
        JevEvaluation jevEvaluation,
        JevError jevError
) {

    public static final String POWERED_BY_AI = "AI (Jev by TypeSafe AI)";
    public static final String POWERED_BY_BASIC = "Basic calculation";

    public DecisionResponse withJevEvaluation(JevEvaluation evaluation) {
        return new DecisionResponse(stockName, averagePurchasePrice, currentPrice, changePercent,
                decision, reason, evaluatedAt, POWERED_BY_AI, evaluation, null);
    }

    public DecisionResponse withJevError(JevError error) {
        return new DecisionResponse(stockName, averagePurchasePrice, currentPrice, changePercent,
                decision, reason, evaluatedAt, POWERED_BY_BASIC, null, error);
    }
}
