package io.github.turmony.seckillsystem.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.turmony.seckillsystem.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀订单Mapper
 */
@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {
}
