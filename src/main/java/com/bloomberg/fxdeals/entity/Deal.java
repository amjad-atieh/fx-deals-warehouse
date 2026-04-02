package com.bloomberg.fxdeals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "deals")
@Data
@NoArgsConstructor
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Deal unique ID must not be blank")
    @Size(max = 255, message = "Deal unique ID cannot exceed 255 characters")
    @Column(name = "deal_unique_id", nullable = false, unique = true)
    private String dealUniqueId;

    @NotBlank(message = "From currency ISO code must not be blank")
    @Pattern(regexp = "^[A-Z]{3}$", message = "From currency must be a 3-letter uppercase ISO code")
    @Column(name = "from_currency_iso_code", nullable = false, columnDefinition = "VARCHAR(3)")
    private String fromCurrencyIsoCode;

    @NotBlank(message = "To currency ISO code must not be blank")
    @Pattern(regexp = "^[A-Z]{3}$", message = "To currency must be a 3-letter uppercase ISO code")
    @Column(name = "to_currency_iso_code", nullable = false, columnDefinition = "VARCHAR(3)")
    private String toCurrencyIsoCode;

    @NotNull(message = "Deal timestamp must not be null")
    @Column(name = "deal_timestamp", nullable = false)
    private LocalDateTime dealTimestamp;

    @NotNull(message = "Deal amount must not be null")
    @Positive(message = "Deal amount must be greater than zero")
    @Column(name = "deal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal dealAmount;
}
