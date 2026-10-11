package com.neighbormarket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neighbormarket.domain.Handover;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HandoverMapper extends BaseMapper<Handover> {
    @Select("""
            SELECT STRAIGHT_JOIN h.id, h.item_id, h.publisher_id, h.recipient_id, h.interest_id,
                   h.start_at, h.end_at, h.location_snapshot, h.status,
                   h.created_at, h.cancelled_at, h.completed_at,
                   i.title AS item_title,
                   publisher.nickname AS publisher_name,
                   recipient.nickname AS recipient_name
            FROM handover h
            JOIN item i ON i.id = h.item_id
            JOIN app_user publisher ON publisher.id = h.publisher_id
            JOIN app_user recipient ON recipient.id = h.recipient_id
            WHERE h.publisher_id = #{userId} OR h.recipient_id = #{userId}
            ORDER BY h.created_at DESC, h.id DESC
            """)
    List<HandoverViewRow> selectViewsByParty(@Param("userId") Long userId);

    @Select("""
            SELECT h.id, h.item_id, h.publisher_id, h.recipient_id, h.interest_id,
                   h.start_at, h.end_at, h.location_snapshot, h.status,
                   h.created_at, h.cancelled_at, h.completed_at,
                   i.title AS item_title,
                   publisher.nickname AS publisher_name,
                   recipient.nickname AS recipient_name
            FROM handover h
            JOIN item i ON i.id = h.item_id
            JOIN app_user publisher ON publisher.id = h.publisher_id
            JOIN app_user recipient ON recipient.id = h.recipient_id
            WHERE h.id = #{id}
            """)
    HandoverViewRow selectViewById(@Param("id") Long id);
}
