package com.neighbormarket.service;

import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.domain.TradeType;
import com.neighbormarket.mapper.AppUserMapper;
import com.neighbormarket.mapper.ItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemServiceUpdateTest {
    @Test
    void onlyPublisherCanEdit() {
        Fixture fixture = fixture(item(9L, 1L, ItemStatus.AVAILABLE, 2L), user(2L, "other"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.update(9L, command(2L), "other"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(fixture.items, never()).updateEditableFields(any(), any());
    }

    @Test
    void staleVersionCannotEdit() {
        Fixture fixture = fixture(item(9L, 1L, ItemStatus.AVAILABLE, 3L), user(1L, "owner"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.update(9L, command(2L), "owner"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(fixture.exchange, never()).hasWaitingInterest(any());
    }

    @Test
    void waitingInterestPreventsEdit() {
        Fixture fixture = fixture(item(9L, 1L, ItemStatus.AVAILABLE, 2L), user(1L, "owner"));
        when(fixture.exchange.hasWaitingInterest(9L)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.update(9L, command(2L), "owner"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(fixture.items, never()).updateEditableFields(any(), any());
    }

    @Test
    void losingConditionalUpdateIsReported() {
        Fixture fixture = fixture(item(9L, 1L, ItemStatus.AVAILABLE, 2L), user(1L, "owner"));
        when(fixture.items.updateEditableFields(any(), any())).thenReturn(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.update(9L, command(2L), "owner"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(fixture.exchange, never()).replaceSlotsForEdit(any(), any(), any());
    }

    @Test
    void successfulEditReplacesSlotsAndReturnsNextVersion() {
        Item locked = item(9L, 1L, ItemStatus.AVAILABLE, 2L);
        AppUser owner = user(1L, "owner");
        Fixture fixture = fixture(locked, owner);
        when(fixture.items.updateEditableFields(any(), any())).thenReturn(1);
        Item updated = item(9L, 1L, ItemStatus.AVAILABLE, 3L);
        updated.setTitle("new title");
        updated.setDescription("new description");
        updated.setCategory("books");
        updated.setTradeType(TradeType.FIXED_PRICE);
        updated.setPrice(new BigDecimal("12.00"));
        updated.setPickupLocation("new gate");
        when(fixture.items.selectById(9L)).thenReturn(updated);
        when(fixture.users.selectById(1L)).thenReturn(owner);
        UpdateItemCommand command = command(2L);

        ItemService.ItemView result = fixture.service.update(9L, command, "owner");

        assertEquals(3L, result.version());
        assertEquals("new title", result.title());
        verify(fixture.items).updateEditableFields(locked, 2L);
        verify(fixture.exchange).replaceSlotsForEdit(
                9L, " new gate ", command.slots());
    }

    private static Fixture fixture(Item item, AppUser user) {
        ItemMapper items = mock(ItemMapper.class);
        AppUserMapper users = mock(AppUserMapper.class);
        ExchangeService exchange = mock(ExchangeService.class);
        when(users.selectOne(any())).thenReturn(user);
        when(items.selectByIdForUpdate(9L)).thenReturn(item);
        return new Fixture(items, users, exchange, new ItemService(items, users, exchange));
    }

    private static UpdateItemCommand command(Long version) {
        return new UpdateItemCommand(
                version, " new title ", " new description ", " books ",
                TradeType.FIXED_PRICE, new BigDecimal("12.00"), " new gate ",
                List.of(new ExchangeService.SlotInput(
                        LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1))));
    }

    private static AppUser user(Long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        return user;
    }

    private static Item item(Long id, Long publisherId, ItemStatus status, Long version) {
        Item item = new Item();
        item.setId(id);
        item.setPublisherId(publisherId);
        item.setStatus(status);
        item.setVersion(version);
        item.setTitle("old title");
        item.setDescription("old description");
        item.setCategory("other");
        item.setTradeType(TradeType.FREE);
        item.setPrice(BigDecimal.ZERO);
        item.setPickupLocation("old gate");
        item.setPublishedAt(LocalDateTime.now().minusDays(1));
        item.setLastConfirmedAt(LocalDateTime.now());
        return item;
    }

    private record Fixture(
            ItemMapper items, AppUserMapper users, ExchangeService exchange, ItemService service) {
    }
}
