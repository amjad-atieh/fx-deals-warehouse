package com.bloomberg.fxdeals.integration;

import com.bloomberg.fxdeals.dto.BatchImportResult;
import com.bloomberg.fxdeals.dto.DealRequest;
import com.bloomberg.fxdeals.dto.DealResponse;
import com.bloomberg.fxdeals.dto.DealStatus;
import com.bloomberg.fxdeals.entity.Deal;
import com.bloomberg.fxdeals.exception.ErrorResponse;
import com.bloomberg.fxdeals.repository.DealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
class DealControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DealRepository dealRepository;

    @BeforeEach
    void setUp() {
        dealRepository.deleteAll();
    }

    @Test
    @DisplayName("Single Deal - Success")
    void shouldSaveSingleDeal() {
        // Given
        DealRequest request = new DealRequest(
                "INT-001",
                "USD",
                "EUR",
                LocalDateTime.now(),
                BigDecimal.valueOf(1000.50)
        );

        // When
        ResponseEntity<DealResponse> response = restTemplate.postForEntity("/api/deals", request, DealResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(DealStatus.SAVED);

        Optional<Deal> savedDeal = dealRepository.findByDealUniqueId("INT-001");
        assertThat(savedDeal).isPresent();
        assertThat(savedDeal.get().getFromCurrencyIsoCode()).isEqualTo("USD");
        assertThat(savedDeal.get().getToCurrencyIsoCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Single Deal - Validation Failure (Missing Field)")
    void shouldFailWhenMissingField() {
        // Given
        DealRequest request = new DealRequest(
                null, // missing field
                "USD",
                "EUR",
                LocalDateTime.now(),
                BigDecimal.valueOf(1000.50)
        );

        // When
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/api/deals", request, ErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).containsIgnoringCase("validation");
        assertThat(response.getBody().details()).containsKey("dealUniqueId");
        assertThat(response.getBody().details().get("dealUniqueId")).isEqualTo("Deal unique ID is required");
    }

    @Test
    @DisplayName("Single Deal - Invalid Currency Format")
    void shouldFailWithInvalidCurrency() {
        // Given
        DealRequest request = new DealRequest(
                "INT-002",
                "US", // Invalid currency format
                "EUR",
                LocalDateTime.now(),
                BigDecimal.valueOf(1000.50)
        );

        // When
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/api/deals", request, ErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details()).containsKey("fromCurrencyIsoCode");
        assertThat(response.getBody().details().get("fromCurrencyIsoCode")).contains("must be a 3-letter");
    }

    @Test
    @DisplayName("Single Deal - Duplicate")
    void shouldHandleDuplicateDeal() {
        // Given
        DealRequest request = new DealRequest(
                "INT-003",
                "USD",
                "EUR",
                LocalDateTime.now(),
                BigDecimal.valueOf(1000.50)
        );

        // First insert
        restTemplate.postForEntity("/api/deals", request, DealResponse.class);

        // When - sending duplicate
        ResponseEntity<DealResponse> response = restTemplate.postForEntity("/api/deals", request, DealResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(DealStatus.DUPLICATE);
        
        // Verify DB still has only one row for that ID
        long count = dealRepository.findAll().stream().filter(d -> d.getDealUniqueId().equals("INT-003")).count();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("Batch Import - All Valid")
    void shouldImportValidBatch() {
        // Given
        List<DealRequest> requests = List.of(
                new DealRequest("BATCH-001", "USD", "EUR", LocalDateTime.now(), BigDecimal.valueOf(100)),
                new DealRequest("BATCH-002", "GBP", "EUR", LocalDateTime.now(), BigDecimal.valueOf(200)),
                new DealRequest("BATCH-003", "JPY", "USD", LocalDateTime.now(), BigDecimal.valueOf(300))
        );

        // When
        ResponseEntity<BatchImportResult> response = restTemplate.postForEntity("/api/deals/batch", requests, BatchImportResult.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().succeeded()).isEqualTo(3);
        assertThat(response.getBody().duplicates()).isEqualTo(0);
        assertThat(response.getBody().failures()).isEqualTo(0);

        assertThat(dealRepository.existsByDealUniqueId("BATCH-001")).isTrue();
        assertThat(dealRepository.existsByDealUniqueId("BATCH-002")).isTrue();
        assertThat(dealRepository.existsByDealUniqueId("BATCH-003")).isTrue();
    }

    @Test
    @DisplayName("Batch Import - Mixed Results (No Rollback)")
    void shouldHandleMixedBatch() {
        // Given
        // Pre-insert one
        Deal deal = new Deal();
        deal.setDealUniqueId("BM-001");
        deal.setFromCurrencyIsoCode("USD");
        deal.setToCurrencyIsoCode("EUR");
        deal.setDealTimestamp(LocalDateTime.now());
        deal.setDealAmount(BigDecimal.valueOf(1000));
        dealRepository.save(deal);

        List<DealRequest> requests = List.of(
                new DealRequest("BM-001", "USD", "EUR", LocalDateTime.now(), BigDecimal.valueOf(1000)), // Duplicate
                new DealRequest("BM-002", "GBP", "EUR", LocalDateTime.now(), BigDecimal.valueOf(200))   // Valid
        );

        // When
        ResponseEntity<BatchImportResult> response = restTemplate.postForEntity("/api/deals/batch", requests, BatchImportResult.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().succeeded()).isEqualTo(1);
        assertThat(response.getBody().duplicates()).isEqualTo(1);
        assertThat(response.getBody().failures()).isEqualTo(0);

        assertThat(dealRepository.existsByDealUniqueId("BM-002")).isTrue();
        long countBM001 = dealRepository.findAll().stream().filter(d -> d.getDealUniqueId().equals("BM-001")).count();
        assertThat(countBM001).isEqualTo(1L);
    }

    @Test
    @DisplayName("Batch Import - Validation Error in One Item")
    void shouldHandleValidationErrorInBatch() {
        // Given
        List<DealRequest> requests = List.of(
                new DealRequest("BATCH-ERR-001", "USD", "EUR", LocalDateTime.now(), BigDecimal.valueOf(100)),
                new DealRequest("BATCH-ERR-002", null, "EUR", LocalDateTime.now(), BigDecimal.valueOf(200)), // Invalid
                new DealRequest("BATCH-ERR-003", "JPY", "USD", LocalDateTime.now(), BigDecimal.valueOf(300))
        );

        // When
        ResponseEntity<BatchImportResult> response = restTemplate.postForEntity("/api/deals/batch", requests, BatchImportResult.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().succeeded()).isEqualTo(2);
        assertThat(response.getBody().failures()).isEqualTo(1);

        assertThat(dealRepository.existsByDealUniqueId("BATCH-ERR-001")).isTrue();
        assertThat(dealRepository.existsByDealUniqueId("BATCH-ERR-002")).isFalse();
        assertThat(dealRepository.existsByDealUniqueId("BATCH-ERR-003")).isTrue();
    }

    @Test
    @DisplayName("Get Deal by Unique ID - Found")
    void shouldFindDealById() {
        // Given
        Deal deal = new Deal();
        deal.setDealUniqueId("FIND-001");
        deal.setFromCurrencyIsoCode("USD");
        deal.setToCurrencyIsoCode("EUR");
        deal.setDealTimestamp(LocalDateTime.now());
        deal.setDealAmount(BigDecimal.valueOf(1000));
        dealRepository.save(deal);

        // When
        ResponseEntity<Deal> response = restTemplate.getForEntity("/api/deals/FIND-001", Deal.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDealUniqueId()).isEqualTo("FIND-001");
        assertThat(response.getBody().getFromCurrencyIsoCode()).isEqualTo("USD");
        assertThat(response.getBody().getToCurrencyIsoCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Get Deal by Unique ID - Not Found")
    void shouldReturnNotFoundWhenDealDoesNotExist() {
        // When
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/deals/NON-EXISTENT", ErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).containsIgnoringCase("NON-EXISTENT").containsIgnoringCase("not find");
    }
}
