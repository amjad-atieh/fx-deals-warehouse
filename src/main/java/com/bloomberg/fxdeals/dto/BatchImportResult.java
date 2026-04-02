package com.bloomberg.fxdeals.dto;

import java.util.List;

public record BatchImportResult(
    int totalReceived,
    int succeeded,
    int duplicates,
    int failures,
    List<DealResponse> details
) {}
