package com.neighbormarket.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.Handover;
import com.neighbormarket.domain.HandoverStatus;
import com.neighbormarket.domain.InterestStatus;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemInterest;
import com.neighbormarket.mapper.AppUserMapper;
import com.neighbormarket.mapper.HandoverMapper;
import com.neighbormarket.mapper.ItemCommentMapper;
import com.neighbormarket.mapper.ItemInterestMapper;
import com.neighbormarket.mapper.ItemMapper;
import com.neighbormarket.mapper.PickupSlotMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateTransitionAuditTest {
    private final ItemMapper items = mock(ItemMapper.class);
    private final AppUserMapper users = mock(AppUserMapper.class);
    private final ItemInterestMapper interests = mock(ItemInterestMapper.class);
    private final HandoverMapper handovers = mock(HandoverMapper.class);
    private final ExchangeService service = new ExchangeService(items, users, mock(PickupSlotMapper.class),
            interests, handovers, mock(ItemCommentMapper.class));

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "audit-item"), Item.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "audit-interest"), ItemInterest.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "audit-handover"), Handover.class);
    }

    @Test
    void confirmedInterestCannotBeCancelledDirectly() {
        when(users.selectOne(any())).thenReturn(user(2));
        when(interests.selectOne(any())).thenReturn(interest(InterestStatus.CONFIRMED));
        assertStatus(HttpStatus.CONFLICT, () -> service.cancelInterest(5L, "recipient"));
        verify(interests, never()).update(isNull(), any());
    }

    @Test
    void cancelledInterestCannotBeConfirmed() {
        when(users.selectOne(any())).thenReturn(user(1));
        when(interests.selectById(8L)).thenReturn(interest(InterestStatus.CANCELLED));
        when(items.selectById(5L)).thenReturn(item());
        assertStatus(HttpStatus.CONFLICT, () -> service.confirm(8L, "publisher"));
        verify(items, never()).update(isNull(), any());
    }

    @Test
    void cancelledHandoverCannotBeCompleted() {
        when(users.selectOne(any())).thenReturn(user(1));
        when(handovers.selectById(20L)).thenReturn(handover(HandoverStatus.CANCELLED));
        assertStatus(HttpStatus.CONFLICT, () -> service.complete(20L, "publisher"));
        verify(handovers, never()).update(isNull(), any());
    }

    @Test
    void completedHandoverCannotBeCancelled() {
        when(users.selectOne(any())).thenReturn(user(2));
        when(handovers.selectById(20L)).thenReturn(handover(HandoverStatus.COMPLETED));
        assertStatus(HttpStatus.CONFLICT, () -> service.cancelHandover(20L, "recipient"));
        verify(handovers, never()).update(isNull(), any());
    }

    @Test
    void recipientCannotCompleteActiveHandover() {
        when(users.selectOne(any())).thenReturn(user(2));
        when(handovers.selectById(20L)).thenReturn(handover(HandoverStatus.ACTIVE));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.complete(20L, "recipient"));
        verify(handovers, never()).update(isNull(), any());
    }

    private static void assertStatus(HttpStatus status, Runnable operation) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, operation::run);
        assertEquals(status, error.getStatusCode());
    }

    private static AppUser user(long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(id == 1 ? "publisher" : "recipient");
        return user;
    }

    private static Item item() {
        Item item = new Item();
        item.setId(5L);
        item.setPublisherId(1L);
        return item;
    }

    private static ItemInterest interest(InterestStatus status) {
        ItemInterest interest = new ItemInterest();
        interest.setId(8L);
        interest.setItemId(5L);
        interest.setUserId(2L);
        interest.setStatus(status);
        return interest;
    }

    private static Handover handover(HandoverStatus status) {
        Handover handover = new Handover();
        handover.setId(20L);
        handover.setItemId(5L);
        handover.setPublisherId(1L);
        handover.setRecipientId(2L);
        handover.setInterestId(8L);
        handover.setStatus(status);
        return handover;
    }
}
