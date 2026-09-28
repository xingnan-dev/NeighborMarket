package com.neighbormarket.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neighbormarket.domain.Item;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface ItemMapper extends BaseMapper<Item> {}
