package com.saurabh.stockdecision.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Percentage thresholds that drive the decision. All values are positive percentages.
 *
 * <ul>
 *   <li>change >= +takeProfitPercent             -> SELL (take profit)</li>
 *   <li>change <= -stopLossPercent               -> SELL (stop loss)</li>
 *   <li>-stopLoss < change <= -averageDownPercent -> BUY  (average down)</li>
 *   <li>otherwise                                -> HOLD</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "decision")
public record DecisionProperties(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal takeProfitPercent,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal stopLossPercent,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal averageDownPercent
) {

    @AssertTrue(message = "decision.average-down-percent must be smaller than decision.stop-loss-percent")
    public boolean isAverageDownBelowStopLoss() {
        return averageDownPercent == null || stopLossPercent == null
                || averageDownPercent.compareTo(stopLossPercent) < 0;
    }
}
