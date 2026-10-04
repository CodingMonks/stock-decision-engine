package com.saurabh.stockdecision.model;

import java.math.BigDecimal;
import java.time.Instant;

public record DecisionResponse(
        String stockName,
        BigDecimal averagePurchasePrice,
        BigDecimal currentPrice,
        BigDecimal changePercent,
        Decision decision,
        String reason,
        Instant evaluatedAt
) {
}
