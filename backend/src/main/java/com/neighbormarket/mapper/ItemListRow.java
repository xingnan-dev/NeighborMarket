package com.neighbormarket.mapper;

import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.domain.TradeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ItemListRow {
    private Long id;
    private Long publisherId;
    private String publisherNickname;
    private String title;
    private String description;
    private String category;
    private TradeType tradeType;
    private BigDecimal price;
    private String imageUrl;
    private ItemStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime lastConfirmedAt;
    private LocalDateTime completedAt;
    private String pickupLocation;
    private Long currentHandoverId;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPublisherId() { return publisherId; }
    public void setPublisherId(Long publisherId) { this.publisherId = publisherId; }
    public String getPublisherNickname() { return publisherNickname; }
    public void setPublisherNickname(String publisherNickname) { this.publisherNickname = publisherNickname; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public TradeType getTradeType() { return tradeType; }
    public void setTradeType(TradeType tradeType) { this.tradeType = tradeType; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public ItemStatus getStatus() { return status; }
    public void setStatus(ItemStatus status) { this.status = status; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getLastConfirmedAt() { return lastConfirmedAt; }
    public void setLastConfirmedAt(LocalDateTime lastConfirmedAt) { this.lastConfirmedAt = lastConfirmedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }
    public Long getCurrentHandoverId() { return currentHandoverId; }
    public void setCurrentHandoverId(Long currentHandoverId) { this.currentHandoverId = currentHandoverId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
