package com.neighbormarket.mapper;

import com.neighbormarket.domain.ItemInterest;

import java.time.LocalDateTime;

public class InterestViewRow extends ItemInterest {
    private String itemTitle;
    private String nickname;
    private LocalDateTime slotStartAt;
    private LocalDateTime slotEndAt;

    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public LocalDateTime getSlotStartAt() { return slotStartAt; }
    public void setSlotStartAt(LocalDateTime slotStartAt) { this.slotStartAt = slotStartAt; }
    public LocalDateTime getSlotEndAt() { return slotEndAt; }
    public void setSlotEndAt(LocalDateTime slotEndAt) { this.slotEndAt = slotEndAt; }
}
