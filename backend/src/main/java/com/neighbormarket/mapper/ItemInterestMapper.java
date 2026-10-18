package com.neighbormarket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neighbormarket.domain.ItemInterest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ItemInterestMapper extends BaseMapper<ItemInterest> {
    @Insert("""
            INSERT INTO item_interest(item_id, user_id, slot_id, status, created_at, updated_at)
            VALUES (#{itemId}, #{userId}, #{slotId}, 'WAITING', #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                slot_id = IF(status = 'CONFIRMED', slot_id, VALUES(slot_id)),
                status = IF(status = 'CONFIRMED', status, VALUES(status)),
                updated_at = IF(status = 'CONFIRMED', updated_at, VALUES(updated_at))
            """)
    int upsertWaiting(@Param("itemId") Long itemId,
                      @Param("userId") Long userId,
                      @Param("slotId") Long slotId,
                      @Param("now") LocalDateTime now);

    @Select("""
            SELECT id, item_id, user_id, slot_id, status, created_at, updated_at
            FROM item_interest
            WHERE item_id = #{itemId} AND user_id = #{userId}
            FOR UPDATE
            """)
    ItemInterest selectByItemAndUserForUpdate(@Param("itemId") Long itemId,
                                              @Param("userId") Long userId);

    @Select("""
            SELECT id
            FROM item_interest
            WHERE item_id = #{itemId} AND status = 'WAITING'
            ORDER BY id
            LIMIT 1
            FOR UPDATE
            """)
    Long selectWaitingIdForUpdate(@Param("itemId") Long itemId);

    @Select("""
            SELECT ii.id, ii.item_id, ii.user_id, ii.slot_id, ii.status,
                   ii.created_at, ii.updated_at,
                   i.title AS item_title, u.nickname,
                   ps.start_at AS slot_start_at, ps.end_at AS slot_end_at
            FROM item_interest ii
            JOIN item i ON i.id = ii.item_id
            JOIN app_user u ON u.id = ii.user_id
            JOIN pickup_slot ps ON ps.id = ii.slot_id
            WHERE ii.item_id = #{itemId}
            ORDER BY ii.updated_at DESC, ii.id DESC
            """)
    List<InterestViewRow> selectViewsByItem(@Param("itemId") Long itemId);

    @Select("""
            SELECT ii.id, ii.item_id, ii.user_id, ii.slot_id, ii.status,
                   ii.created_at, ii.updated_at,
                   i.title AS item_title, u.nickname,
                   ps.start_at AS slot_start_at, ps.end_at AS slot_end_at
            FROM item_interest ii
            JOIN item i ON i.id = ii.item_id
            JOIN app_user u ON u.id = ii.user_id
            JOIN pickup_slot ps ON ps.id = ii.slot_id
            WHERE ii.item_id = #{itemId} AND ii.user_id = #{userId}
            """)
    InterestViewRow selectViewByItemAndUser(@Param("itemId") Long itemId,
                                            @Param("userId") Long userId);

    @Select("""
            SELECT ii.id, ii.item_id, ii.user_id, ii.slot_id, ii.status,
                   ii.created_at, ii.updated_at,
                   i.title AS item_title, u.nickname,
                   ps.start_at AS slot_start_at, ps.end_at AS slot_end_at
            FROM item_interest ii
            JOIN item i ON i.id = ii.item_id
            JOIN app_user u ON u.id = ii.user_id
            JOIN pickup_slot ps ON ps.id = ii.slot_id
            WHERE ii.user_id = #{userId}
            ORDER BY ii.updated_at DESC, ii.id DESC
            """)
    List<InterestViewRow> selectViewsByUser(@Param("userId") Long userId);
}
