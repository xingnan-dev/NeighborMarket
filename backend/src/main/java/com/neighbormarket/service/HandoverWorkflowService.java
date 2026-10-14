package com.neighbormarket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.Handover;
import com.neighbormarket.domain.HandoverStatus;
import com.neighbormarket.domain.InterestStatus;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemInterest;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.domain.PickupSlot;
import com.neighbormarket.mapper.HandoverMapper;
import com.neighbormarket.mapper.HandoverViewRow;
import com.neighbormarket.mapper.ItemInterestMapper;
import com.neighbormarket.mapper.PickupSlotMapper;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
class HandoverWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(HandoverWorkflowService.class);
    private final ExchangeSupport support;
    private final PickupSlotMapper slots;
    private final ItemInterestMapper interests;
    private final HandoverMapper handovers;

    HandoverWorkflowService(ExchangeSupport support, PickupSlotMapper slots,
                            ItemInterestMapper interests, HandoverMapper handovers) {
        this.support = support;
        this.slots = slots;
        this.interests = interests;
        this.handovers = handovers;
    }

    @Transactional
    ExchangeService.HandoverView confirm(Long interestId, String username) {
        AppUser publisher = support.user(username);
        ItemInterest interest = interests.selectById(interestId);
        if (interest == null) throw support.error(HttpStatus.NOT_FOUND, "意向不存在");
        Item item = support.item(interest.getItemId());
        if (!item.getPublisherId().equals(publisher.getId()))
            throw support.error(HttpStatus.FORBIDDEN, "只有发布者可以确认领取人");
        if (interest.getStatus() != InterestStatus.WAITING)
            throw support.error(HttpStatus.CONFLICT, "意向已不是等待状态");
        PickupSlot slot = slots.selectById(interest.getSlotId());
        if (slot == null || !slot.getItemId().equals(item.getId())
                || !Boolean.TRUE.equals(slot.getActive())
                || !slot.getStartAt().isAfter(LocalDateTime.now()))
            throw support.error(HttpStatus.CONFLICT, "交接时段已失效");
        if (item.getPickupLocation() == null || item.getPickupLocation().isBlank())
            throw support.error(HttpStatus.CONFLICT, "请先设置交接地点");

        int won = support.items.update(null, Wrappers.lambdaUpdate(Item.class)
                .eq(Item::getId, item.getId())
                .eq(Item::getStatus, ItemStatus.AVAILABLE)
                .isNull(Item::getCurrentHandoverId)
                .set(Item::getStatus, ItemStatus.RESERVED));
        if (won != 1) throw support.error(HttpStatus.CONFLICT, "物品已由另一请求确认，请刷新");

        LocalDateTime now = LocalDateTime.now();
        Handover handover = new Handover();
        handover.setItemId(item.getId());
        handover.setPublisherId(publisher.getId());
        handover.setRecipientId(interest.getUserId());
        handover.setInterestId(interest.getId());
        handover.setStartAt(slot.getStartAt());
        handover.setEndAt(slot.getEndAt());
        handover.setLocationSnapshot(item.getPickupLocation());
        handover.setStatus(HandoverStatus.ACTIVE);
        handover.setCreatedAt(now);
        if (handovers.insert(handover) != 1 || handover.getId() == null)
            throw support.error(HttpStatus.CONFLICT, "交接记录创建失败，请重试");
        int linked = support.items.update(null, Wrappers.lambdaUpdate(Item.class)
                .eq(Item::getId, item.getId())
                .eq(Item::getStatus, ItemStatus.RESERVED)
                .isNull(Item::getCurrentHandoverId)
                .set(Item::getCurrentHandoverId, handover.getId()));
        if (linked != 1) throw support.error(HttpStatus.CONFLICT, "物品交接状态已变化，请刷新");
        int confirmed = interests.update(null, Wrappers.lambdaUpdate(ItemInterest.class)
                .eq(ItemInterest::getId, interest.getId())
                .eq(ItemInterest::getStatus, InterestStatus.WAITING)
                .set(ItemInterest::getStatus, InterestStatus.CONFIRMED)
                .set(ItemInterest::getUpdatedAt, now));
        if (confirmed != 1) throw support.error(HttpStatus.CONFLICT, "意向状态已变化，请刷新");
        log.info("Handover state changed; handoverId={} itemId={} publisherId={} recipientId={} state={}",
                handover.getId(), item.getId(), publisher.getId(), interest.getUserId(), HandoverStatus.ACTIVE);
        return support.handoverView(handover);
    }

    List<ExchangeService.HandoverView> mine(String username) {
        return viewsByUser(support.user(username).getId());
    }

    List<ExchangeService.HandoverView> viewsByUser(Long userId) {
        return handovers.selectViewsByParty(userId).stream().map(support::handoverView).toList();
    }

    ExchangeService.HandoverView get(Long id, String username) {
        AppUser user = support.user(username);
        HandoverViewRow handover = handovers.selectViewById(id);
        if (handover == null) throw support.error(HttpStatus.NOT_FOUND, "交接不存在");
        if (!handover.getPublisherId().equals(user.getId())
                && !handover.getRecipientId().equals(user.getId()))
            throw support.error(HttpStatus.FORBIDDEN, "只有交接双方可以查看");
        return support.handoverView(handover);
    }

    @Transactional
    ExchangeService.HandoverView cancel(Long id, String username) {
        AppUser user = support.user(username);
        Handover handover = handovers.selectById(id);
        if (handover == null) throw support.error(HttpStatus.NOT_FOUND, "交接不存在");
        if (!handover.getPublisherId().equals(user.getId())
                && !handover.getRecipientId().equals(user.getId()))
            throw support.error(HttpStatus.FORBIDDEN, "只有交接双方可以取消");
        if (handover.getStatus() != HandoverStatus.ACTIVE)
            throw support.error(HttpStatus.CONFLICT, "该交接已结束，不能重复取消");
        LocalDateTime now = LocalDateTime.now();
        int changed = handovers.update(null, Wrappers.lambdaUpdate(Handover.class)
                .eq(Handover::getId, id).eq(Handover::getStatus, HandoverStatus.ACTIVE)
                .set(Handover::getStatus, HandoverStatus.CANCELLED)
                .set(Handover::getCancelledAt, now));
        if (changed != 1) throw support.error(HttpStatus.CONFLICT, "交接状态已变化");
        int restored = support.items.update(null, Wrappers.lambdaUpdate(Item.class)
                .eq(Item::getId, handover.getItemId()).eq(Item::getStatus, ItemStatus.RESERVED)
                .eq(Item::getCurrentHandoverId, id)
                .set(Item::getStatus, ItemStatus.AVAILABLE)
                .set(Item::getCurrentHandoverId, null));
        if (restored != 1) throw support.error(HttpStatus.CONFLICT, "物品状态与交接不一致");
        int cancelled = interests.update(null, Wrappers.lambdaUpdate(ItemInterest.class)
                .eq(ItemInterest::getId, handover.getInterestId())
                .eq(ItemInterest::getStatus, InterestStatus.CONFIRMED)
                .set(ItemInterest::getStatus, InterestStatus.CANCELLED)
                .set(ItemInterest::getUpdatedAt, now));
        if (cancelled != 1) throw support.error(HttpStatus.CONFLICT, "意向状态与交接不一致");
        log.info("Handover state changed; handoverId={} itemId={} actorId={} state={}",
                id, handover.getItemId(), user.getId(), HandoverStatus.CANCELLED);
        return support.handoverView(handovers.selectById(id));
    }

    @Transactional
    ExchangeService.HandoverView complete(Long id, String username) {
        AppUser user = support.user(username);
        Handover handover = handovers.selectById(id);
        if (handover == null) throw support.error(HttpStatus.NOT_FOUND, "交接不存在");
        if (!handover.getPublisherId().equals(user.getId()))
            throw support.error(HttpStatus.FORBIDDEN, "只有发布者可以标记已送出");
        if (handover.getStatus() != HandoverStatus.ACTIVE)
            throw support.error(HttpStatus.CONFLICT, "该交接已结束，不能重复完成");
        LocalDateTime now = LocalDateTime.now();
        int changed = handovers.update(null, Wrappers.lambdaUpdate(Handover.class)
                .eq(Handover::getId, id).eq(Handover::getStatus, HandoverStatus.ACTIVE)
                .set(Handover::getStatus, HandoverStatus.COMPLETED)
                .set(Handover::getCompletedAt, now));
        if (changed != 1) throw support.error(HttpStatus.CONFLICT, "交接状态已变化");
        int done = support.items.update(null, Wrappers.lambdaUpdate(Item.class)
                .eq(Item::getId, handover.getItemId()).eq(Item::getStatus, ItemStatus.RESERVED)
                .eq(Item::getCurrentHandoverId, id)
                .set(Item::getStatus, ItemStatus.COMPLETED)
                .set(Item::getCompletedAt, now));
        if (done != 1) throw support.error(HttpStatus.CONFLICT, "物品状态与交接不一致");
        log.info("Handover state changed; handoverId={} itemId={} actorId={} state={}",
                id, handover.getItemId(), user.getId(), HandoverStatus.COMPLETED);
        return support.handoverView(handovers.selectById(id));
    }
}
