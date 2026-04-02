package com.bloomberg.fxdeals.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DealRequest(
    @NotBlank(message = "Deal unique ID is required")
    String dealUniqueId,

    @NotBlank(message = "From currency ISO code is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "From currency must be a 3-letter uppercase ISO code")
    String fromCurrencyIsoCode,

    @NotBlank(message = "To currency ISO code is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "To currency must be a 3-letter uppercase ISO code")
    String toCurrencyIsoCode,

    @NotNull(message = "Deal timestamp is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDateTime dealTimestamp,

    @NotNull(message = "Deal amount is required")
    @Positive(message = "Deal amount must be greater than zero")
    BigDecimal dealAmount
) {}
