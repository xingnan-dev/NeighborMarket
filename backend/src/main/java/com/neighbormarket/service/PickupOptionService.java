package com.neighbormarket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.InterestStatus;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemInterest;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.domain.PickupSlot;
import com.neighbormarket.mapper.ItemInterestMapper;
import com.neighbormarket.mapper.PickupSlotMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
class PickupOptionService {
    private final ExchangeSupport support;
    private final PickupSlotMapper slots;
    private final ItemInterestMapper interests;

    PickupOptionService(ExchangeSupport support, PickupSlotMapper slots,
                        ItemInterestMapper interests) {
        this.support = support;
        this.slots = slots;
        this.interests = interests;
    }

    List<ExchangeService.SlotView> validSlots(Long itemId) {
        LocalDateTime now = LocalDateTime.now();
        return slots.selectList(Wrappers.lambdaQuery(PickupSlot.class)
                        .eq(PickupSlot::getItemId, itemId)
                        .eq(PickupSlot::getActive, true)
                        .gt(PickupSlot::getStartAt, now)
                        .orderByAsc(PickupSlot::getStartAt))
                .stream().map(support::slotView).toList();
    }

    @Transactional
    List<ExchangeService.SlotView> configure(Long itemId, String location,
                                             List<ExchangeService.SlotInput> input,
                                             String username) {
        AppUser user = support.user(username);
        Item item = support.item(itemId);
        if (!item.getPublisherId().equals(user.getId()))
            throw support.error(HttpStatus.FORBIDDEN, "只有发布者可以设置交接信息");
        if (item.getStatus() != ItemStatus.AVAILABLE)
            throw support.error(HttpStatus.CONFLICT, "待交接或已送出物品不能修改时段");
        validateLocationAndInput(location, input);
        if (interests.selectCount(Wrappers.lambdaQuery(ItemInterest.class)
                .eq(ItemInterest::getItemId, itemId)
                .eq(ItemInterest::getStatus, InterestStatus.WAITING)) > 0)
            throw support.error(HttpStatus.CONFLICT, "已有等待意向，不能修改时段");

        LocalDateTime now = LocalDateTime.now();
        input.forEach(slot -> validateSlot(slot, now));
        slots.update(null, Wrappers.lambdaUpdate(PickupSlot.class)
                .eq(PickupSlot::getItemId, itemId)
                .eq(PickupSlot::getActive, true)
                .set(PickupSlot::getActive, false));
        item.setPickupLocation(location.trim());
        support.items.updateById(item);
        input.forEach(slot -> insertSlot(itemId, slot, now));
        return validSlots(itemId);
    }

    void addInitialSlots(Item item, List<ExchangeService.SlotInput> input) {
        validateLocationAndInput(item.getPickupLocation(), input);
        LocalDateTime now = LocalDateTime.now();
        input.forEach(slot -> {
            validateSlot(slot, now);
            insertSlot(item.getId(), slot, now);
        });
    }

    private void validateLocationAndInput(String location, List<ExchangeService.SlotInput> input) {
        if (location == null || location.isBlank() || location.trim().length() > 200)
            throw support.error(HttpStatus.BAD_REQUEST, "请输入200字以内的公共交接地点");
        if (input == null || input.isEmpty() || input.size() > 5)
            throw support.error(HttpStatus.BAD_REQUEST, "请提供1至5个交接时段");
    }

    private void validateSlot(ExchangeService.SlotInput input, LocalDateTime now) {
        if (input == null || input.startAt() == null || input.endAt() == null)
            throw support.error(HttpStatus.BAD_REQUEST, "时段不能为空");
        if (!input.startAt().isAfter(now))
            throw support.error(HttpStatus.BAD_REQUEST, "时段必须尚未开始");
        if (!input.endAt().isAfter(input.startAt()))
            throw support.error(HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
    }

    private void insertSlot(Long itemId, ExchangeService.SlotInput input, LocalDateTime now) {
        PickupSlot slot = new PickupSlot();
        slot.setItemId(itemId);
        slot.setStartAt(input.startAt());
        slot.setEndAt(input.endAt());
        slot.setActive(true);
        slot.setCreatedAt(now);
        slots.insert(slot);
    }
}
