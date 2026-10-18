package com.neighbormarket.service;

import com.neighbormarket.domain.TradeType;

import java.math.BigDecimal;
import java.util.List;

public record UpdateItemCommand(
        Long expectedVersion,
        String title,
        String description,
        String category,
        TradeType tradeType,
        BigDecimal price,
        String pickupLocation,
        List<ExchangeService.SlotInput> slots) {
}
