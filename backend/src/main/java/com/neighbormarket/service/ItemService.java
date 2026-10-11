package com.neighbormarket.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.domain.TradeType;
import com.neighbormarket.mapper.AppUserMapper;
import com.neighbormarket.mapper.ItemListRow;
import com.neighbormarket.mapper.ItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class ItemService {
    public static final String PLACEHOLDER_IMAGE =
            "https://placehold.co/800x600/F4EBDD/37534A?text=NeighborMarket";
    public static final String DISPLAY_TIME_ZONE = "Asia/Shanghai";

    private final ItemMapper items;
    private final AppUserMapper users;
    private final ExchangeService exchange;

    @Autowired
    public ItemService(ItemMapper items, AppUserMapper users, ExchangeService exchange) {
        this.items = items;
        this.users = users;
        this.exchange = exchange;
    }

    public ItemService(ItemMapper items, AppUserMapper users) {
        this(items, users, null);
    }

    public IPage<ItemView> market(
            Page<Item> page,
            String keyword,
            String category,
            TradeType tradeType,
            ItemStatus status) {
        ItemStatus target = status == null ? ItemStatus.AVAILABLE : status;
        String normalizedKeyword = normalizeFilter(keyword);
        String normalizedCategory = normalizeFilter(category);
        return items.selectMarketPage(page, normalizedKeyword, normalizedCategory, tradeType, target)
                .convert(this::view);
    }

    public ItemView detail(Long id) {
        Item item = items.selectById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "物品不存在");
        }
        return view(item);
    }

    @Transactional
    public ItemView create(CreateItemCommand command, String username) {
        AppUser publisher = findUser(username);
        BigDecimal price = normalizePrice(command.tradeType(), command.price());
        LocalDateTime now = LocalDateTime.now();

        Item item = new Item();
        item.setPublisherId(publisher.getId());
        item.setTitle(command.title().trim());
        item.setDescription(command.description().trim());
        item.setCategory(command.category().trim());
        item.setTradeType(command.tradeType());
        item.setPrice(price);
        item.setImageUrl(PLACEHOLDER_IMAGE);
        item.setStatus(ItemStatus.AVAILABLE);
        item.setPublishedAt(now);
        item.setLastConfirmedAt(now);
        item.setPickupLocation(command.pickupLocation().trim());
        items.insert(item);
        exchange.addInitialSlots(item, command.slots());
        return view(item);
    }

    public IPage<ItemView> mine(Page<Item> page, String username) {
        AppUser user = findUser(username);
        return items.selectMinePage(page, user.getId()).convert(this::view);
    }

    @Transactional
    public ItemView confirmAvailable(Long id, String username) {
        AppUser user = findUser(username);
        Item item = items.selectById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "物品不存在");
        }
        if (!item.getPublisherId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有发布者可以确认物品状态");
        }
        if (item.getStatus() != ItemStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有可领取物品可以确认还在");
        }

        LocalDateTime now = LocalDateTime.now();
        int changed = items.update(null, Wrappers.lambdaUpdate(Item.class)
                .eq(Item::getId, id)
                .eq(Item::getPublisherId, user.getId())
                .eq(Item::getStatus, ItemStatus.AVAILABLE)
                .set(Item::getLastConfirmedAt, now));
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "物品状态已变化，请刷新后重试");
        }
        return view(items.selectById(id));
    }

    static BigDecimal normalizePrice(TradeType tradeType, BigDecimal price) {
        if (tradeType == TradeType.FIXED_PRICE) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("标价必须大于0元");
            }
            return price;
        }
        if (price != null) {
            throw new IllegalArgumentException(
                    tradeType == TradeType.FREE ? "免费送无需填写价格" : "随便给无需填写价格");
        }
        return tradeType == TradeType.FREE ? BigDecimal.ZERO : null;
    }

    private static String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AppUser findUser(String username) {
        return users.selectOne(Wrappers.lambdaQuery(AppUser.class)
                .eq(AppUser::getUsername, username));
    }

    private ItemView view(Item item) {
        AppUser publisher = users.selectById(item.getPublisherId());
        return view(
                item.getId(), item.getTitle(), item.getDescription(), item.getCategory(),
                item.getTradeType(), item.getPrice(), item.getImageUrl(), item.getStatus(),
                item.getPublishedAt(), item.getLastConfirmedAt(), item.getCompletedAt(),
                item.getPickupLocation(), item.getCurrentHandoverId(),
                new PublisherView(publisher.getId(), publisher.getNickname()));
    }

    private ItemView view(ItemListRow item) {
        return view(
                item.getId(), item.getTitle(), item.getDescription(), item.getCategory(),
                item.getTradeType(), item.getPrice(), item.getImageUrl(), item.getStatus(),
                item.getPublishedAt(), item.getLastConfirmedAt(), item.getCompletedAt(),
                item.getPickupLocation(), item.getCurrentHandoverId(),
                new PublisherView(item.getPublisherId(), item.getPublisherNickname()));
    }

    private ItemView view(
            Long id,
            String title,
            String description,
            String category,
            TradeType tradeType,
            BigDecimal price,
            String imageUrl,
            ItemStatus status,
            LocalDateTime publishedAt,
            LocalDateTime lastConfirmedAt,
            LocalDateTime completedAt,
            String pickupLocation,
            Long currentHandoverId,
            PublisherView publisher) {
        LocalDateTime now = LocalDateTime.now();
        return new ItemView(
                id, title, description, category, tradeType, price, imageUrl, status,
                publishedAt, lastConfirmedAt, completedAt, pickupLocation, currentHandoverId,
                FreshnessRules.label(publishedAt, now),
                FreshnessRules.isConfirmationStale(lastConfirmedAt, now),
                DISPLAY_TIME_ZONE, publisher);
    }

    public record PublisherView(Long id, String nickname) {
    }

    public record ItemView(
            Long id,
            String title,
            String description,
            String category,
            TradeType tradeType,
            BigDecimal price,
            String imageUrl,
            ItemStatus status,
            LocalDateTime publishedAt,
            LocalDateTime lastConfirmedAt,
            LocalDateTime completedAt,
            String pickupLocation,
            Long currentHandoverId,
            String freshnessLabel,
            boolean confirmationStale,
            String displayTimeZone,
            PublisherView publisher) {
    }
}
