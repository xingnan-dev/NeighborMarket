package com.neighbormarket.web;

import com.neighbormarket.domain.TradeType;
import com.neighbormarket.service.CreateItemCommand;
import com.neighbormarket.web.dto.ExchangeRequests;
import com.neighbormarket.web.dto.ItemRequests;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDtoValidationTest {
    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void createItemCascadesValidationIntoPickupSlots() {
        ItemRequests.CreateItem request = new ItemRequests.CreateItem(
                "书架", "九成新", "家具", TradeType.FREE, null, "社区门口",
                List.of(new ItemRequests.PickupSlot(null, LocalDateTime.now().plusDays(1))));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("slots[0].startAt");
    }

    @Test
    void pickupOptionsCascadesValidationAndWantRequiresPositiveSlotId() {
        ExchangeRequests.PickupOptions pickupOptions = new ExchangeRequests.PickupOptions(
                "社区门口", List.of(new ItemRequests.PickupSlot(LocalDateTime.now(), null)));

        assertThat(validator.validate(pickupOptions))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("slots[0].endAt");
        assertThat(validator.validate(new ExchangeRequests.WantItem(0L, 0L)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("slotId");
        assertThat(validator.validate(new ExchangeRequests.WantItem(1L, -1L)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("expectedItemVersion");
    }

    @Test
    void validHttpRequestMapsToServiceCommandWithoutChangingFields() {
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        ItemRequests.CreateItem request = new ItemRequests.CreateItem(
                "书架", "九成新", "家具", TradeType.FREE, null, "社区门口",
                List.of(new ItemRequests.PickupSlot(start, start.plusHours(1))));

        assertThat(validator.validate(request)).isEmpty();
        CreateItemCommand command = request.toCommand();
        assertThat(command.title()).isEqualTo(request.title());
        assertThat(command.tradeType()).isEqualTo(request.tradeType());
        assertThat(command.slots()).singleElement().satisfies(slot -> {
            assertThat(slot.startAt()).isEqualTo(start);
            assertThat(slot.endAt()).isEqualTo(start.plusHours(1));
        });
    }
}
