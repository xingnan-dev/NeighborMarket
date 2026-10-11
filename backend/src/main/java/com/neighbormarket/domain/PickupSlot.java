package com.neighbormarket.domain;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime;
@TableName("pickup_slot") public class PickupSlot {
 @TableId(type=IdType.AUTO) private Long id; private Long itemId; private LocalDateTime startAt; private LocalDateTime endAt; private Boolean active; private LocalDateTime createdAt;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getItemId(){return itemId;} public void setItemId(Long v){itemId=v;}
 public LocalDateTime getStartAt(){return startAt;} public void setStartAt(LocalDateTime v){startAt=v;} public LocalDateTime getEndAt(){return endAt;} public void setEndAt(LocalDateTime v){endAt=v;}
 public Boolean getActive(){return active;} public void setActive(Boolean v){active=v;}
 public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
