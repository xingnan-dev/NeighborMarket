package com.neighbormarket.service;

import com.neighbormarket.domain.*;
import com.neighbormarket.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ItemServiceConfirmTest {
    @Test void reservedItemCannotBeConfirmed() {
        AppUserMapper users = mock(AppUserMapper.class);
        ItemMapper items = mock(ItemMapper.class);
        var user = user(1L, "neighbor_a");
        var item = item(9L, 1L, ItemStatus.RESERVED);
        when(users.selectOne(any())).thenReturn(user);
        when(items.selectById(9L)).thenReturn(item);
        var error = assertThrows(ResponseStatusException.class,
                () -> new ItemService(items, users).confirmAvailable(9L, "neighbor_a"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(items, never()).update(any(), any());
    }

    @Test void nonPublisherCannotConfirm() {
        AppUserMapper users = mock(AppUserMapper.class);
        ItemMapper items = mock(ItemMapper.class);
        when(users.selectOne(any())).thenReturn(user(2L, "neighbor_b"));
        when(items.selectById(9L)).thenReturn(item(9L, 1L, ItemStatus.AVAILABLE));
        var error = assertThrows(ResponseStatusException.class,
                () -> new ItemService(items, users).confirmAvailable(9L, "neighbor_b"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(items, never()).update(any(), any());
    }

    private static AppUser user(Long id, String username) {
        var user = new AppUser(); user.setId(id); user.setUsername(username); return user;
    }
    private static Item item(Long id, Long publisherId, ItemStatus status) {
        var item = new Item(); item.setId(id); item.setPublisherId(publisherId); item.setStatus(status); return item;
    }
}