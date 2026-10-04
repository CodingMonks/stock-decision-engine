package com.saurabh.stockdecision.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Input supplied by the caller. All data needed for the decision is in the request,
 * so the service never calls an external system.
 */
public record DecisionRequest(

        @Schema(example = "AAPL")
        @NotBlank(message = "stockName is required")
        @Size(max = 20, message = "stockName must be at most 20 characters")
        String stockName,

        @Schema(example = "150.00")
        @NotNull(message = "averagePurchasePrice is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "averagePurchasePrice must be greater than 0")
        @Digits(integer = 12, fraction = 4, message = "averagePurchasePrice has too many digits")
        BigDecimal averagePurchasePrice,

        @Schema(example = "185.00")
        @NotNull(message = "currentPrice is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "currentPrice must be greater than 0")
        @Digits(integer = 12, fraction = 4, message = "currentPrice has too many digits")
        BigDecimal currentPrice
) {
}
