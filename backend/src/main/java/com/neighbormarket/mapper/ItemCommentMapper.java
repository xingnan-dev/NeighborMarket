package com.neighbormarket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neighbormarket.domain.ItemComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ItemCommentMapper extends BaseMapper<ItemComment> {
    @Select("""
            SELECT c.id, c.item_id, c.user_id, c.parent_id, c.content, c.created_at,
                   u.nickname
            FROM item_comment c
            JOIN app_user u ON u.id = c.user_id
            WHERE c.item_id = #{itemId}
            ORDER BY c.created_at ASC, c.id ASC
            """)
    List<CommentViewRow> selectViewsByItem(@Param("itemId") Long itemId);
}
