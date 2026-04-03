package com.bloomberg.fxdeals.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DealEntityTest {

    @Test
    @DisplayName("Deal entity: should correctly set and get all properties")
    void shouldSetAndGetProperties() {
        Deal deal = new Deal();
        LocalDateTime now = LocalDateTime.now();
        BigDecimal amount = new BigDecimal("123.45");

        deal.setId(1L);
        deal.setDealUniqueId("UNIQUE-123");
        deal.setFromCurrencyIsoCode("USD");
        deal.setToCurrencyIsoCode("EUR");
        deal.setDealTimestamp(now);
        deal.setDealAmount(amount);

        assertThat(deal.getId()).isEqualTo(1L);
        assertThat(deal.getDealUniqueId()).isEqualTo("UNIQUE-123");
        assertThat(deal.getFromCurrencyIsoCode()).isEqualTo("USD");
        assertThat(deal.getToCurrencyIsoCode()).isEqualTo("EUR");
        assertThat(deal.getDealTimestamp()).isEqualTo(now);
        assertThat(deal.getDealAmount()).isEqualTo(amount);
        
        // Also test toString, equals and hashCode derived from Lombok
        Deal deal2 = new Deal();
        deal2.setId(1L);
        deal2.setDealUniqueId("UNIQUE-123");
        deal2.setFromCurrencyIsoCode("USD");
        deal2.setToCurrencyIsoCode("EUR");
        deal2.setDealTimestamp(now);
        deal2.setDealAmount(amount);
        
        assertThat(deal).isEqualTo(deal2);
        assertThat(deal.hashCode()).isEqualTo(deal2.hashCode());
        assertThat(deal.toString()).contains("UNIQUE-123", "USD", "EUR");
    }
}
