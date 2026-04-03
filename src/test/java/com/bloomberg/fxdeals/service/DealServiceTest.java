package com.bloomberg.fxdeals.service;

import com.bloomberg.fxdeals.dto.BatchImportResult;
import com.bloomberg.fxdeals.dto.DealRequest;
import com.bloomberg.fxdeals.dto.DealResponse;
import com.bloomberg.fxdeals.dto.DealStatus;
import com.bloomberg.fxdeals.entity.Deal;
import com.bloomberg.fxdeals.exception.DealNotFoundException;
import com.bloomberg.fxdeals.repository.DealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DealService}.
 * Focuses on business logic, deduplication, batch processing, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    @Mock
    private DealRepository dealRepository;

    @InjectMocks
    private DealService dealService;

    @BeforeEach
    void setUp() {
        // Any common setup can be placed here.
        // MockitoExtension ensures mocks are initialized before each test.
    }

    // --- Helper Methods for Test Data ---

    /**
     * Creates a valid DealRequest with the given parameters.
     */
    private DealRequest createValidDealRequest(String uniqueId, String fromCurr, String toCurr, LocalDateTime timestamp, BigDecimal amount) {
        return new DealRequest(uniqueId, fromCurr, toCurr, timestamp, amount);
    }

    /**
     * Creates a valid Deal entity with the given parameters and a preset ID.
     */
    private Deal createValidDealEntity(Long id, String uniqueId, String fromCurr, String toCurr, LocalDateTime timestamp, BigDecimal amount) {
        Deal deal = new Deal();
        deal.setId(id);
        deal.setDealUniqueId(uniqueId);
        deal.setFromCurrencyIsoCode(fromCurr);
        deal.setToCurrencyIsoCode(toCurr);
        deal.setDealTimestamp(timestamp);
        deal.setDealAmount(amount);
        return deal;
    }

    // --- Single Deal Tests ---

    /**
     * Tests that a new, non-existent deal is saved successfully and returns SAVED status.
     * Expected behavior: repository.existsByDealUniqueId returns false, repository.saveAndFlush returns saved entity.
     */
    @Test
    @DisplayName("Single deal: success when unique ID not present")
    void shouldSaveNewDeal_whenUniqueIdDoesNotExist() {
        // Given
        DealRequest request = createValidDealRequest("FX-001", "USD", "EUR", LocalDateTime.now(), new BigDecimal("1000.00"));
        
        when(dealRepository.existsByDealUniqueId("FX-001")).thenReturn(false);
        when(dealRepository.saveAndFlush(any(Deal.class))).thenAnswer(inv -> {
            Deal d = inv.getArgument(0);
            d.setId(1L); // simulate DB generated ID
            return d;
        });

        // When
        DealResponse response = dealService.importSingleDeal(request);

        // Then
        assertThat(response.status()).isEqualTo(DealStatus.SAVED);
        assertThat(response.message()).containsIgnoringCase("success");
        
        // Verify repository interactions
        verify(dealRepository, times(1)).existsByDealUniqueId("FX-001");
        verify(dealRepository, times(1)).saveAndFlush(any(Deal.class));
    }

    /**
     * Tests that an existing deal is detected during the pre-check and not saved again.
     * Expected behavior: repository.existsByDealUniqueId returns true, repository.saveAndFlush is never called.
     */
    @Test
    @DisplayName("Single deal: duplicate detected (pre-check)")
    void shouldReturnDuplicate_whenUniqueIdAlreadyExists() {
        // Given
        DealRequest request = createValidDealRequest("FX-002", "GBP", "USD", LocalDateTime.now(), new BigDecimal("500.00"));
        
        when(dealRepository.existsByDealUniqueId("FX-002")).thenReturn(true);

        // When
        DealResponse response = dealService.importSingleDeal(request);

        // Then
        assertThat(response.status()).isEqualTo(DealStatus.DUPLICATE);
        assertThat(response.message()).containsIgnoringCase("already exists");
        
        // Verify repository interactions
        verify(dealRepository, times(1)).existsByDealUniqueId("FX-002");
        verify(dealRepository, never()).saveAndFlush(any(Deal.class));
    }

    /**
     * Tests handling of concurrent inserts that bypass the exists-check and trigger DB constraints.
     * Expected behavior: saveAndFlush throws DataIntegrityViolationException, service recovers and returns DUPLICATE.
     */
    @Test
    @DisplayName("Single deal: race condition / DB constraint violation")
    void shouldReturnDuplicate_whenDataIntegrityViolationOccurs() {
        // Given
        DealRequest request = createValidDealRequest("FX-003", "EUR", "JPY", LocalDateTime.now(), new BigDecimal("2000.00"));
        
        when(dealRepository.existsByDealUniqueId("FX-003")).thenReturn(false);
        when(dealRepository.saveAndFlush(any(Deal.class))).thenThrow(new DataIntegrityViolationException("Constraint violation"));

        // When
        DealResponse response = dealService.importSingleDeal(request);

        // Then
        assertThat(response.status()).isEqualTo(DealStatus.DUPLICATE);
        assertThat(response.message()).containsIgnoringCase("already exists");
        
        // Verify repository interactions
        verify(dealRepository, times(1)).existsByDealUniqueId("FX-003");
        verify(dealRepository, times(1)).saveAndFlush(any(Deal.class));
    }

    /**
     * Tests boundary condition where DealRequest is completely null.
     * This relies on Java's NullPointerException because request.dealUniqueId() is invoked without null checks in the service.
     */
    @Test
    @DisplayName("Single deal: handles null request boundary condition gracefully")
    void shouldThrowException_whenRequestIsNull() {
        // Given
        DealRequest request = null;

        // When & Then
        // Controller layer validates @NotNull so this isn't expected via HTTP,
        // but unit testing service isolation verifies we get an exception.
        assertThatThrownBy(() -> dealService.importSingleDeal(request))
                .isInstanceOf(NullPointerException.class);
        
        verify(dealRepository, never()).existsByDealUniqueId(anyString());
        verify(dealRepository, never()).saveAndFlush(any(Deal.class));
    }

    /**
     * Tests that a deal with extremely large values correctly persists.
     */
    @Test
    @DisplayName("Single deal: processes boundary / extremely large amounts successfully")
    void shouldSaveDeal_withExtremelyLargeAmount() {
        // Given
        BigDecimal massiveAmount = BigDecimal.valueOf(1e12);
        DealRequest request = createValidDealRequest("FX-MASSIVE", "USD", "EUR", LocalDateTime.now(), massiveAmount);
        
        when(dealRepository.existsByDealUniqueId("FX-MASSIVE")).thenReturn(false);
        when(dealRepository.saveAndFlush(any(Deal.class))).thenAnswer(inv -> {
            Deal d = inv.getArgument(0);
            d.setId(99L);
            return d;
        });

        // When
        DealResponse response = dealService.importSingleDeal(request);

        // Then
        assertThat(response.status()).isEqualTo(DealStatus.SAVED);
        verify(dealRepository, times(1)).saveAndFlush(any(Deal.class));
    }

    // --- Batch Deal Tests ---

    /**
     * Tests batch processing where all imported deals are completely new and valid.
     * Expected behavior: Each deal passes the pre-check and is successfully saved.
     */
    @Test
    @DisplayName("Batch import: all deals succeed")
    void shouldProcessBatchDeals_whenAllDealsAreValidAndUnique() {
        // Given
        DealRequest req1 = createValidDealRequest("NEW-001", "USD", "EUR", LocalDateTime.now(), new BigDecimal("100.00"));
        DealRequest req2 = createValidDealRequest("NEW-002", "GBP", "USD", LocalDateTime.now(), new BigDecimal("200.00"));
        DealRequest req3 = createValidDealRequest("NEW-003", "EUR", "JPY", LocalDateTime.now(), new BigDecimal("300.00"));

        List<DealRequest> requests = Arrays.asList(req1, req2, req3);

        when(dealRepository.existsByDealUniqueId(anyString())).thenReturn(false);
        when(dealRepository.saveAndFlush(any(Deal.class))).thenAnswer(inv -> {
            Deal d = inv.getArgument(0);
            d.setId(System.nanoTime()); // simulate DB generated ID
            return d;
        });

        // When
        BatchImportResult result = dealService.importBatchDeals(requests);

        // Then
        assertThat(result.totalReceived()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(3);
        assertThat(result.duplicates()).isEqualTo(0);
        assertThat(result.failures()).isEqualTo(0);

        assertThat(result.details()).hasSize(3);
        assertThat(result.details().get(0).status()).isEqualTo(DealStatus.SAVED);
        assertThat(result.details().get(1).status()).isEqualTo(DealStatus.SAVED);
        assertThat(result.details().get(2).status()).isEqualTo(DealStatus.SAVED);

        // Verify counts
        verify(dealRepository, times(3)).existsByDealUniqueId(anyString());
        verify(dealRepository, times(3)).saveAndFlush(any(Deal.class));
    }

    /**
     * Tests batch processing with mixed results: one new deal, one duplicate, one new deal.
     * Determines that failure or duplication in one deal does not rollback or halt the entire batch.
     */
    @Test
    @DisplayName("Batch import: no rollback, mixed success/duplicate results")
    void shouldProcessBatchDeals_withMixedResults() {
        // Given
        DealRequest validReq1 = createValidDealRequest("B-001", "USD", "EUR", LocalDateTime.now(), new BigDecimal("100.00"));
        DealRequest duplicateReq = createValidDealRequest("B-002", "GBP", "USD", LocalDateTime.now(), new BigDecimal("200.00"));
        DealRequest validReq2 = createValidDealRequest("B-003", "EUR", "JPY", LocalDateTime.now(), new BigDecimal("300.00"));
        
        List<DealRequest> requests = Arrays.asList(validReq1, duplicateReq, validReq2);

        // Mock behaviors
        when(dealRepository.existsByDealUniqueId("B-001")).thenReturn(false);
        when(dealRepository.existsByDealUniqueId("B-002")).thenReturn(true); // Pre-check catches duplicate
        when(dealRepository.existsByDealUniqueId("B-003")).thenReturn(false);
        
        when(dealRepository.saveAndFlush(any(Deal.class))).thenAnswer(inv -> {
            Deal d = inv.getArgument(0);
            d.setId(System.nanoTime()); // Generate pseudo-ID
            return d;
        });

        // When
        BatchImportResult result = dealService.importBatchDeals(requests);

        // Then
        assertThat(result.totalReceived()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.failures()).isEqualTo(0);
        
        // Verify details statuses
        assertThat(result.details()).hasSize(3);
        assertThat(result.details().get(0).status()).isEqualTo(DealStatus.SAVED);
        assertThat(result.details().get(1).status()).isEqualTo(DealStatus.DUPLICATE);
        assertThat(result.details().get(2).status()).isEqualTo(DealStatus.SAVED);
        
        // Verify repository save was called exactly twice (for the two valid requests)
        verify(dealRepository, times(2)).saveAndFlush(any(Deal.class));

        // Verify order of calls
        InOrder inOrder = inOrder(dealRepository);
        inOrder.verify(dealRepository).existsByDealUniqueId("B-001");
        inOrder.verify(dealRepository).saveAndFlush(any(Deal.class));
        inOrder.verify(dealRepository).existsByDealUniqueId("B-002");
        // No save for B-002
        inOrder.verify(dealRepository).existsByDealUniqueId("B-003");
        inOrder.verify(dealRepository).saveAndFlush(any(Deal.class));
    }

    /**
     * Tests batch processing resilience against severe unexpected exceptions.
     * Expected behavior: Exception on one item logs and marks failure, but rest of batch continues.
     */
    @Test
    @DisplayName("Batch import: partial failure (unexpected exception)")
    void shouldProcessBatchDeals_withPartialUnexpectedFailure() {
        // Given
        DealRequest validReq = createValidDealRequest("BAD-001", "USD", "EUR", LocalDateTime.now(), new BigDecimal("100.00"));
        DealRequest failingReq = createValidDealRequest("BAD-002", "GBP", "USD", LocalDateTime.now(), new BigDecimal("200.00"));
        
        List<DealRequest> requests = Arrays.asList(validReq, failingReq);

        when(dealRepository.existsByDealUniqueId(anyString())).thenReturn(false);
        
        // Match specific requests for distinct behavior
        when(dealRepository.saveAndFlush(Mockito.argThat(d -> d != null && "BAD-001".equals(d.getDealUniqueId())))).thenAnswer(inv -> {
            Deal d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });
        when(dealRepository.saveAndFlush(Mockito.argThat(d -> d != null && "BAD-002".equals(d.getDealUniqueId())))).thenThrow(new RuntimeException("DB Connection Dropped"));

        // When
        BatchImportResult result = dealService.importBatchDeals(requests);

        // Then
        assertThat(result.totalReceived()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.duplicates()).isEqualTo(0);
        assertThat(result.failures()).isEqualTo(1);

        assertThat(result.details()).hasSize(2);
        assertThat(result.details().get(0).status()).isEqualTo(DealStatus.SAVED);
        assertThat(result.details().get(1).status()).isEqualTo(DealStatus.ERROR);
        
        // Save was attempted twice
        verify(dealRepository, times(2)).saveAndFlush(any(Deal.class));
    }

    /**
     * Tests batch deal functionality against extremely small/empty workloads.
     */
    @Test
    @DisplayName("Batch import: handles empty list")
    void shouldReturnZeroCounters_whenBatchListIsEmpty() {
        // Given
        List<DealRequest> emptyList = Collections.emptyList();

        // When
        BatchImportResult result = dealService.importBatchDeals(emptyList);

        // Then
        assertThat(result.totalReceived()).isEqualTo(0);
        assertThat(result.succeeded()).isEqualTo(0);
        assertThat(result.duplicates()).isEqualTo(0);
        assertThat(result.failures()).isEqualTo(0);
        assertThat(result.details()).isEmpty();
        
        // Repository should not have been accessed
        verify(dealRepository, never()).existsByDealUniqueId(anyString());
        verify(dealRepository, never()).saveAndFlush(any(Deal.class));
    }

    // --- Entity Retrieval Tests ---

    /**
     * Tests basic deal fetching by unique ID.
     */
    @Test
    @DisplayName("Get deal: successfully retrieves entity by unique ID")
    void shouldGetDealByUniqueId_whenIdExists() {
        // Given
        Deal mockDeal = createValidDealEntity(10L, "FIND-ME", "USD", "EUR", LocalDateTime.now(), new BigDecimal("123.45"));
        when(dealRepository.findByDealUniqueId("FIND-ME")).thenReturn(Optional.of(mockDeal));

        // When
        Deal retrievedDeal = dealService.getDealByUniqueId("FIND-ME");

        // Then
        assertThat(retrievedDeal).isNotNull();
        assertThat(retrievedDeal.getId()).isEqualTo(10L);
        assertThat(retrievedDeal.getDealUniqueId()).isEqualTo("FIND-ME");
    }

    /**
     * Tests fetching an unknown deal unique ID properly triggers DealNotFoundException.
     */
    @Test
    @DisplayName("Get deal: throws proper exception when not found")
    void shouldThrowDealNotFoundException_whenIdDoesNotExist() {
        // Given
        when(dealRepository.findByDealUniqueId("UNKNOWN-DEAL")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> dealService.getDealByUniqueId("UNKNOWN-DEAL"))
                .isInstanceOf(DealNotFoundException.class)
                .hasMessageContaining("Could not find deal with unique id: UNKNOWN-DEAL");
    }
}