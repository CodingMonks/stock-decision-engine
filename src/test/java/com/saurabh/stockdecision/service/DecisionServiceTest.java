package com.saurabh.stockdecision.service;

import com.saurabh.stockdecision.config.DecisionProperties;
import com.saurabh.stockdecision.model.Decision;
import com.saurabh.stockdecision.model.DecisionRequest;
import com.saurabh.stockdecision.model.DecisionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC);
    private final DecisionService service = new DecisionService(
            new DecisionProperties(new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("5")),
            fixedClock);

    @ParameterizedTest(name = "avg={0}, current={1} -> {2} ({3}%)")
    @CsvSource({
            "100, 130, SELL, 30.00",   // take profit
            "100, 120, SELL, 20.00",   // exactly at take-profit boundary
            "100, 119.99, HOLD, 19.99",
            "100, 100, HOLD, 0.00",
            "100, 95.01, HOLD, -4.99",
            "100, 95, BUY, -5.00",     // exactly at average-down boundary
            "100, 90, BUY, -10.00",
            "100, 85.01, BUY, -14.99",
            "100, 85, SELL, -15.00",   // exactly at stop-loss boundary
            "100, 50, SELL, -50.00"
    })
    void appliesThresholds(String avg, String current, Decision expected, String expectedPct) {
        DecisionResponse r = service.decide(new DecisionRequest("aapl", new BigDecimal(avg), new BigDecimal(current)));

        assertThat(r.decision()).isEqualTo(expected);
        assertThat(r.changePercent()).isEqualByComparingTo(expectedPct);
    }

    @Test
    void normalisesStockNameAndStampsTime() {
        DecisionResponse r = service.decide(new DecisionRequest("  msft ", BigDecimal.TEN, BigDecimal.TEN));

        assertThat(r.stockName()).isEqualTo("MSFT");
        assertThat(r.evaluatedAt()).isEqualTo(Instant.parse("2026-10-04T12:00:00Z"));
        assertThat(r.reason()).isNotBlank();
    }
}
