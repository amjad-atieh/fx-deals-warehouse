package com.bloomberg.fxdeals.controller;

import com.bloomberg.fxdeals.dto.BatchImportResult;
import com.bloomberg.fxdeals.dto.DealRequest;
import com.bloomberg.fxdeals.dto.DealResponse;
import com.bloomberg.fxdeals.dto.DealStatus;
import com.bloomberg.fxdeals.entity.Deal;
import com.bloomberg.fxdeals.service.DealService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DealController.class)
class DealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DealService dealService;

    @Test
    @DisplayName("POST /api/deals - returns 201 when saved")
    void importDeal_saved_returnsCreated() throws Exception {
        DealRequest request = createValidDealRequest();
        DealResponse response = new DealResponse("123", DealStatus.SAVED, "Saved successfully");
        when(dealService.importSingleDeal(any())).thenReturn(response);

        mockMvc.perform(post("/api/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SAVED"));
    }

    @Test
    @DisplayName("POST /api/deals - returns 409 when duplicate")
    void importDeal_duplicate_returnsConflict() throws Exception {
        DealRequest request = createValidDealRequest();
        DealResponse response = new DealResponse("123", DealStatus.DUPLICATE, "Duplicate deal");
        when(dealService.importSingleDeal(any())).thenReturn(response);

        mockMvc.perform(post("/api/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));
    }

    @Test
    @DisplayName("POST /api/deals - returns 500 when error")
    void importDeal_error_returnsInternalServerError() throws Exception {
        DealRequest request = createValidDealRequest();
        DealResponse response = new DealResponse("123", DealStatus.ERROR, "Some error");
        when(dealService.importSingleDeal(any())).thenReturn(response);

        mockMvc.perform(post("/api/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    @DisplayName("POST /api/deals/batch - processing batch deals returns 200 with result")
    void importBatchDeals_returnsOk() throws Exception {
        List<DealRequest> requests = List.of(createValidDealRequest());
        BatchImportResult result = new BatchImportResult(1, 1, 0, 0, List.of(new DealResponse("123", DealStatus.SAVED, "Ok")));
        when(dealService.importBatchDeals(any())).thenReturn(result);

        mockMvc.perform(post("/api/deals/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived").value(1))
                .andExpect(jsonPath("$.succeeded").value(1));
    }

    @Test
    @DisplayName("GET /api/deals/{dealUniqueId} - returns 200 and deal")
    void getDeal_returnsOk() throws Exception {
        Deal deal = new Deal();
        deal.setDealUniqueId("123");
        deal.setFromCurrencyIsoCode("USD");
        deal.setToCurrencyIsoCode("EUR");
        deal.setDealAmount(BigDecimal.TEN);
        deal.setDealTimestamp(LocalDateTime.now());

        when(dealService.getDealByUniqueId("123")).thenReturn(deal);

        mockMvc.perform(get("/api/deals/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealUniqueId").value("123"))
                .andExpect(jsonPath("$.fromCurrencyIsoCode").value("USD"));
    }

    private DealRequest createValidDealRequest() {
        return new DealRequest(
                "123",
                "USD",
                "EUR",
                LocalDateTime.parse("2024-04-03T10:00:00"),
                BigDecimal.valueOf(100.5)
        );
    }
}
