package com.neighbormarket.service;

import com.neighbormarket.domain.TradeType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ItemServiceTest {
    @Test void fixedPriceMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> ItemService.normalizePrice(TradeType.FIXED_PRICE, null));
        assertThrows(IllegalArgumentException.class, () -> ItemService.normalizePrice(TradeType.FIXED_PRICE, BigDecimal.ZERO));
        assertEquals(new BigDecimal("12.50"), ItemService.normalizePrice(TradeType.FIXED_PRICE, new BigDecimal("12.50")));
    }
    @Test void nonFixedPriceRejectsSubmittedAmount() {
        assertThrows(IllegalArgumentException.class, () -> ItemService.normalizePrice(TradeType.FREE, BigDecimal.ONE));
        assertEquals(BigDecimal.ZERO, ItemService.normalizePrice(TradeType.FREE, null));
        assertNull(ItemService.normalizePrice(TradeType.NEGOTIABLE, null));
    }
}
