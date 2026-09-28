package com.neighbormarket.domain;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime;
@TableName("item_comment") public class ItemComment {
 @TableId(type=IdType.AUTO) private Long id; private Long itemId; private Long userId; private Long parentId; private String content; private LocalDateTime createdAt;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getItemId(){return itemId;} public void setItemId(Long v){itemId=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public Long getParentId(){return parentId;} public void setParentId(Long v){parentId=v;} public String getContent(){return content;} public void setContent(String v){content=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}