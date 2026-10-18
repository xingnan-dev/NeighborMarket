package com.neighbormarket.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.domain.TradeType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {
    @Select("SELECT * FROM item WHERE id = #{id} FOR UPDATE")
    Item selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE item
            SET title = #{item.title}, description = #{item.description},
                category = #{item.category}, trade_type = #{item.tradeType},
                price = #{item.price}, pickup_location = #{item.pickupLocation},
                version = version + 1
            WHERE id = #{item.id}
              AND publisher_id = #{item.publisherId}
              AND status = 'AVAILABLE'
              AND version = #{expectedVersion}
            """)
    int updateEditableFields(@Param("item") Item item,
                             @Param("expectedVersion") Long expectedVersion);

    @Select("""
            <script>
            SELECT i.id, i.publisher_id,
                   (SELECT u.nickname FROM app_user u WHERE u.id = i.publisher_id) AS publisher_nickname,
                   i.title, i.description, i.category, i.trade_type, i.price,
                   i.image_url, i.status, i.published_at, i.last_confirmed_at,
                   i.completed_at, i.pickup_location, i.current_handover_id, i.version
            FROM item i
            WHERE i.status = #{status}
            <if test="keyword != null and keyword != ''">
              AND (i.title LIKE CONCAT('%%', #{keyword}, '%%')
                   OR i.description LIKE CONCAT('%%', #{keyword}, '%%'))
            </if>
            <if test="category != null and category != ''">
              AND i.category = #{category}
            </if>
            <if test="tradeType != null">
              AND i.trade_type = #{tradeType}
            </if>
            ORDER BY i.published_at DESC, i.id DESC
            </script>
            """)
    IPage<ItemListRow> selectMarketPage(IPage<?> page,
                                        @Param("keyword") String keyword,
                                        @Param("category") String category,
                                        @Param("tradeType") TradeType tradeType,
                                        @Param("status") ItemStatus status);

    @Select("""
            SELECT i.id, i.publisher_id,
                   (SELECT u.nickname FROM app_user u WHERE u.id = i.publisher_id) AS publisher_nickname,
                   i.title, i.description, i.category, i.trade_type, i.price,
                   i.image_url, i.status, i.published_at, i.last_confirmed_at,
                   i.completed_at, i.pickup_location, i.current_handover_id, i.version
            FROM item i
            WHERE i.publisher_id = #{publisherId}
            ORDER BY i.published_at DESC, i.id DESC
            """)
    IPage<ItemListRow> selectMinePage(IPage<?> page, @Param("publisherId") Long publisherId);
}
