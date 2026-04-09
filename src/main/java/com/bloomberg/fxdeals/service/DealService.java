package com.bloomberg.fxdeals.service;

import com.bloomberg.fxdeals.dto.BatchImportResult;
import com.bloomberg.fxdeals.dto.DealRequest;
import com.bloomberg.fxdeals.dto.DealResponse;
import com.bloomberg.fxdeals.dto.DealStatus;
import com.bloomberg.fxdeals.entity.Deal;
import com.bloomberg.fxdeals.exception.DealNotFoundException;
import com.bloomberg.fxdeals.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealService {

    private final DealRepository dealRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DealResponse importSingleDeal(DealRequest request) {
        if (dealRepository.existsByDealUniqueId(request.dealUniqueId())) {
            log.warn("Deal already exists (duplicate): {}", request.dealUniqueId());
            return new DealResponse(request.dealUniqueId(), DealStatus.DUPLICATE, "Deal already exists");
        }

        Deal deal = new Deal();
        deal.setDealUniqueId(request.dealUniqueId());
        deal.setFromCurrencyIsoCode(request.fromCurrencyIsoCode());
        deal.setToCurrencyIsoCode(request.toCurrencyIsoCode());
        deal.setDealTimestamp(request.dealTimestamp());
        deal.setDealAmount(request.dealAmount());

        try {
            dealRepository.saveAndFlush(deal);
            log.info("Successfully saved newly imported deal: {}", request.dealUniqueId());
            return new DealResponse(request.dealUniqueId(), DealStatus.SAVED, "Deal successfully imported");
        } catch (DataIntegrityViolationException ex) {
            log.warn("Race condition detected, deal already exists (duplicate): {}", request.dealUniqueId());
            return new DealResponse(request.dealUniqueId(), DealStatus.DUPLICATE, "Deal already exists");
        } catch (Exception ex) {
            log.error("Failed to save imported deal with ID: {}", request.dealUniqueId(), ex);
            return new DealResponse(request.dealUniqueId(), DealStatus.ERROR, "Unexpected error occurred during save");
        }
    }

    public BatchImportResult importBatchDeals(List<DealRequest> requests) {
        int succeeded = 0;
        int duplicates = 0;
        int failures = 0;
        List<DealResponse> details = new ArrayList<>();

        log.info("Starting batch import process for {} deals", requests.size());

        for (DealRequest request : requests) {
            try {
                DealResponse response = importSingleDeal(request);
                details.add(response);

                switch (response.status()) {
                    case SAVED -> succeeded++;
                    case DUPLICATE -> duplicates++;
                    default -> failures++; // in case of ERROR
                }
            } catch (Exception ex) {
                log.error("Severe batch failure on item {}: {}", request.dealUniqueId(), ex.getMessage());
                failures++;
                details.add(new DealResponse(request.dealUniqueId(), DealStatus.ERROR, "Failed due to internal logic trap"));
            }
        }
        
        log.info("Finished batch import. Passed: {}, Duplicates: {}, Failures: {}", succeeded, duplicates, failures);
        return new BatchImportResult(requests.size(), succeeded, duplicates, failures, details);
    }

    @Transactional(readOnly = true)
    public Deal getDealByUniqueId(String dealUniqueId) {
        return dealRepository.findByDealUniqueId(dealUniqueId)
                .orElseThrow(() -> new DealNotFoundException("Could not find deal with unique id: " + dealUniqueId));
    }
}
