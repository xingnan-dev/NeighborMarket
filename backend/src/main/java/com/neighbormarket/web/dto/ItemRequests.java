package com.neighbormarket.web.dto;

import com.neighbormarket.domain.TradeType;
import com.neighbormarket.service.CreateItemCommand;
import com.neighbormarket.service.ExchangeService;
import com.neighbormarket.service.UpdateItemCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ItemRequests {
    private ItemRequests() {
    }

    public record CreateItem(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 50) String category,
            @NotNull TradeType tradeType,
            @Digits(integer = 8, fraction = 2) BigDecimal price,
            @NotBlank @Size(max = 200) String pickupLocation,
            @Valid @NotEmpty @Size(max = 5) List<PickupSlot> slots) {

        public CreateItemCommand toCommand() {
            return new CreateItemCommand(
                    title,
                    description,
                    category,
                    tradeType,
                    price,
                    pickupLocation,
                    slots.stream().map(PickupSlot::toServiceInput).toList());
        }
    }

    public record UpdateItem(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 50) String category,
            @NotNull TradeType tradeType,
            @Digits(integer = 8, fraction = 2) BigDecimal price,
            @NotBlank @Size(max = 200) String pickupLocation,
            @Valid @NotEmpty @Size(max = 5) List<PickupSlot> slots) {

        public UpdateItemCommand toCommand() {
            return new UpdateItemCommand(
                    expectedVersion, title, description, category, tradeType, price,
                    pickupLocation,
                    slots.stream().map(PickupSlot::toServiceInput).toList());
        }
    }

    public record PickupSlot(
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt) {

        public ExchangeService.SlotInput toServiceInput() {
            return new ExchangeService.SlotInput(startAt, endAt);
        }
    }
}
