package com.saurabh.stockdecision.service;

import com.saurabh.stockdecision.config.DecisionProperties;
import com.saurabh.stockdecision.model.Decision;
import com.saurabh.stockdecision.model.DecisionRequest;
import com.saurabh.stockdecision.model.DecisionResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * Pure, in-memory rules engine. No network, database or third-party calls.
 */
@Service
public class DecisionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DecisionProperties props;
    private final Clock clock;

    public DecisionService(DecisionProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public DecisionResponse decide(DecisionRequest request) {
        BigDecimal avg = request.averagePurchasePrice();
        BigDecimal current = request.currentPrice();

        BigDecimal changePercent = current.subtract(avg)
                .multiply(HUNDRED)
                .divide(avg, 2, RoundingMode.HALF_UP);

        Decision decision;
        String reason;

        if (changePercent.compareTo(props.takeProfitPercent()) >= 0) {
            decision = Decision.SELL;
            reason = "Gain of %s%% reached the take-profit target of +%s%%."
                    .formatted(changePercent, props.takeProfitPercent());
        } else if (changePercent.compareTo(props.stopLossPercent().negate()) <= 0) {
            decision = Decision.SELL;
            reason = "Loss of %s%% breached the stop-loss limit of -%s%%."
                    .formatted(changePercent, props.stopLossPercent());
        } else if (changePercent.compareTo(props.averageDownPercent().negate()) <= 0) {
            decision = Decision.BUY;
            reason = "Price is %s%% below your average; within the average-down band (-%s%% to -%s%%)."
                    .formatted(changePercent, props.averageDownPercent(), props.stopLossPercent());
        } else {
            decision = Decision.HOLD;
            reason = "Change of %s%% is within the hold range (-%s%% to +%s%%)."
                    .formatted(changePercent, props.averageDownPercent(), props.takeProfitPercent());
        }

        return new DecisionResponse(
                request.stockName().trim().toUpperCase(Locale.ROOT),
                avg,
                current,
                changePercent,
                decision,
                reason,
                Instant.now(clock));
    }
}
