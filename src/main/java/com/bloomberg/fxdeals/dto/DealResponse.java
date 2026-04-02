package com.bloomberg.fxdeals.dto;

public record DealResponse(
    String dealUniqueId,
    DealStatus status,
    String message
) {}
