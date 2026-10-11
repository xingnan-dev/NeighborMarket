package com.neighbormarket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.Handover;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemComment;
import com.neighbormarket.domain.ItemInterest;
import com.neighbormarket.domain.PickupSlot;
import com.neighbormarket.mapper.AppUserMapper;
import com.neighbormarket.mapper.CommentViewRow;
import com.neighbormarket.mapper.HandoverViewRow;
import com.neighbormarket.mapper.InterestViewRow;
import com.neighbormarket.mapper.ItemMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class ExchangeSupport {
    final ItemMapper items;
    final AppUserMapper users;

    ExchangeSupport(ItemMapper items, AppUserMapper users) {
        this.items = items;
        this.users = users;
    }

    ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    AppUser user(String username) {
        AppUser user = users.selectOne(Wrappers.lambdaQuery(AppUser.class)
                .eq(AppUser::getUsername, username));
        if (user == null) throw error(HttpStatus.UNAUTHORIZED, "请先登录");
        return user;
    }

    Item item(Long itemId) {
        Item item = items.selectById(itemId);
        if (item == null) throw error(HttpStatus.NOT_FOUND, "物品不存在");
        return item;
    }

    ExchangeService.SlotView slotView(PickupSlot slot) {
        return new ExchangeService.SlotView(
                slot.getId(), slot.getItemId(), slot.getStartAt(), slot.getEndAt());
    }

    ExchangeService.InterestView interestView(ItemInterest interest) {
        AppUser user = users.selectById(interest.getUserId());
        Item item = items.selectById(interest.getItemId());
        return new ExchangeService.InterestView(
                interest.getId(), interest.getItemId(), item == null ? "" : item.getTitle(),
                interest.getUserId(), user == null ? "" : user.getNickname(),
                interest.getSlotId(), null, null, interest.getStatus(), interest.getUpdatedAt());
    }

    ExchangeService.InterestView interestView(ItemInterest interest, PickupSlot slot) {
        AppUser user = users.selectById(interest.getUserId());
        Item item = items.selectById(interest.getItemId());
        return new ExchangeService.InterestView(
                interest.getId(), interest.getItemId(), item == null ? "" : item.getTitle(),
                interest.getUserId(), user == null ? "" : user.getNickname(), interest.getSlotId(),
                slot == null ? null : slot.getStartAt(), slot == null ? null : slot.getEndAt(),
                interest.getStatus(), interest.getUpdatedAt());
    }

    ExchangeService.InterestView interestView(InterestViewRow interest) {
        return new ExchangeService.InterestView(
                interest.getId(), interest.getItemId(), interest.getItemTitle(),
                interest.getUserId(), interest.getNickname(), interest.getSlotId(),
                interest.getSlotStartAt(), interest.getSlotEndAt(),
                interest.getStatus(), interest.getUpdatedAt());
    }

    ExchangeService.HandoverView handoverView(Handover handover) {
        Item item = items.selectById(handover.getItemId());
        AppUser publisher = users.selectById(handover.getPublisherId());
        AppUser recipient = users.selectById(handover.getRecipientId());
        return new ExchangeService.HandoverView(
                handover.getId(), handover.getItemId(), item == null ? "" : item.getTitle(),
                handover.getPublisherId(), publisher == null ? "" : publisher.getNickname(),
                handover.getRecipientId(), recipient == null ? "" : recipient.getNickname(),
                handover.getStartAt(), handover.getEndAt(), handover.getLocationSnapshot(),
                handover.getStatus(), handover.getCreatedAt(), handover.getCancelledAt(),
                handover.getCompletedAt());
    }

    ExchangeService.HandoverView handoverView(HandoverViewRow handover) {
        return new ExchangeService.HandoverView(
                handover.getId(), handover.getItemId(), handover.getItemTitle(),
                handover.getPublisherId(), handover.getPublisherName(),
                handover.getRecipientId(), handover.getRecipientName(), handover.getStartAt(),
                handover.getEndAt(), handover.getLocationSnapshot(), handover.getStatus(),
                handover.getCreatedAt(), handover.getCancelledAt(), handover.getCompletedAt());
    }

    ExchangeService.CommentView commentView(ItemComment comment) {
        AppUser user = users.selectById(comment.getUserId());
        return new ExchangeService.CommentView(
                comment.getId(), comment.getItemId(), comment.getUserId(),
                user == null ? "" : user.getNickname(), comment.getParentId(),
                comment.getContent(), comment.getCreatedAt());
    }

    ExchangeService.CommentView commentView(CommentViewRow comment) {
        return new ExchangeService.CommentView(
                comment.getId(), comment.getItemId(), comment.getUserId(), comment.getNickname(),
                comment.getParentId(), comment.getContent(), comment.getCreatedAt());
    }
}
