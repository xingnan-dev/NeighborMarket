package com.neighbormarket.web.dto;

import com.neighbormarket.service.ExchangeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ExchangeRequests {
    private ExchangeRequests() {
    }

    public record PickupOptions(
            @NotBlank @Size(max = 200) String location,
            @Valid @NotEmpty @Size(max = 5) List<ItemRequests.PickupSlot> slots) {

        public List<ExchangeService.SlotInput> serviceSlots() {
            return slots.stream().map(ItemRequests.PickupSlot::toServiceInput).toList();
        }
    }

    public record WantItem(
            @NotNull @Positive Long slotId,
            @NotNull @PositiveOrZero Long expectedItemVersion) {
    }

    public record Comment(@NotBlank @Size(max = 500) String content) {
    }
}
