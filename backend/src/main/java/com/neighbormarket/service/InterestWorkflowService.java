package com.neighbormarket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.InterestStatus;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemInterest;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.domain.PickupSlot;
import com.neighbormarket.mapper.InterestViewRow;
import com.neighbormarket.mapper.ItemInterestMapper;
import com.neighbormarket.mapper.PickupSlotMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
class InterestWorkflowService {
    private final ExchangeSupport support;
    private final PickupSlotMapper slots;
    private final ItemInterestMapper interests;

    InterestWorkflowService(ExchangeSupport support, PickupSlotMapper slots,
                            ItemInterestMapper interests) {
        this.support = support;
        this.slots = slots;
        this.interests = interests;
    }

    @Transactional
    ExchangeService.InterestView want(Long itemId, Long slotId, String username) {
        AppUser user = support.user(username);
        Item item = support.item(itemId);
        if (item.getPublisherId().equals(user.getId()))
            throw support.error(HttpStatus.FORBIDDEN, "不能想要自己发布的物品");
        if (item.getStatus() != ItemStatus.AVAILABLE)
            throw support.error(HttpStatus.CONFLICT, "物品当前不可领取");
        PickupSlot slot = slots.selectById(slotId);
        if (slot == null || !slot.getItemId().equals(itemId))
            throw support.error(HttpStatus.BAD_REQUEST, "时段不属于该物品");
        if (!Boolean.TRUE.equals(slot.getActive()) || !slot.getStartAt().isAfter(LocalDateTime.now()))
            throw support.error(HttpStatus.CONFLICT, "该时段已失效或过期");

        LocalDateTime now = LocalDateTime.now();
        interests.upsertWaiting(itemId, user.getId(), slotId, now);
        ItemInterest interest = interests.selectByItemAndUserForUpdate(itemId, user.getId());
        if (interest == null)
            throw support.error(HttpStatus.CONFLICT, "意向状态写入失败，请重试");
        if (interest.getStatus() == InterestStatus.CONFIRMED)
            throw support.error(HttpStatus.CONFLICT, "该意向已被确认");
        return support.interestView(interest, slot);
    }

    @Transactional
    ExchangeService.InterestView cancel(Long itemId, String username) {
        AppUser user = support.user(username);
        ItemInterest interest = interests.selectOne(Wrappers.lambdaQuery(ItemInterest.class)
                .eq(ItemInterest::getItemId, itemId)
                .eq(ItemInterest::getUserId, user.getId()));
        if (interest == null) throw support.error(HttpStatus.NOT_FOUND, "意向不存在");
        if (interest.getStatus() != InterestStatus.WAITING)
            throw support.error(HttpStatus.CONFLICT, "只有等待中的意向可以取消");
        LocalDateTime now = LocalDateTime.now();
        int cancelled = interests.update(null, Wrappers.lambdaUpdate(ItemInterest.class)
                .eq(ItemInterest::getId, interest.getId())
                .eq(ItemInterest::getStatus, InterestStatus.WAITING)
                .set(ItemInterest::getStatus, InterestStatus.CANCELLED)
                .set(ItemInterest::getUpdatedAt, now));
        if (cancelled != 1) throw support.error(HttpStatus.CONFLICT, "意向状态已变化，请刷新");
        interest.setStatus(InterestStatus.CANCELLED);
        interest.setUpdatedAt(now);
        return support.interestView(interest, slots.selectById(interest.getSlotId()));
    }

    List<ExchangeService.InterestView> itemInterests(Long itemId, String username) {
        AppUser user = support.user(username);
        Item item = support.item(itemId);
        if (!item.getPublisherId().equals(user.getId()))
            throw support.error(HttpStatus.FORBIDDEN, "只有发布者可以查看完整意向名单");
        return interests.selectViewsByItem(itemId).stream().map(support::interestView).toList();
    }

    ExchangeService.InterestView mine(Long itemId, String username) {
        AppUser user = support.user(username);
        InterestViewRow interest = interests.selectViewByItemAndUser(itemId, user.getId());
        return interest == null ? null : support.interestView(interest);
    }
}
