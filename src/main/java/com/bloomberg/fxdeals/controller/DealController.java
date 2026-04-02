package com.bloomberg.fxdeals.controller;

import com.bloomberg.fxdeals.dto.BatchImportResult;
import com.bloomberg.fxdeals.dto.DealRequest;
import com.bloomberg.fxdeals.dto.DealResponse;
import com.bloomberg.fxdeals.entity.Deal;
import com.bloomberg.fxdeals.service.DealService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deals")
@Validated
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @PostMapping
    public ResponseEntity<DealResponse> importDeal(@Valid @RequestBody DealRequest request) {
        DealResponse response = dealService.importSingleDeal(request);
        
        return switch (response.status()) {
            case SAVED -> ResponseEntity.status(HttpStatus.CREATED).body(response);
            case DUPLICATE -> ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        };
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchImportResult> importBatchDeals(@RequestBody @Valid List<@Valid DealRequest> requests) {
        BatchImportResult result = dealService.importBatchDeals(requests);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{dealUniqueId}")
    public ResponseEntity<Deal> getDeal(@PathVariable String dealUniqueId) {
        Deal deal = dealService.getDealByUniqueId(dealUniqueId);
        return ResponseEntity.ok(deal);
    }
}
