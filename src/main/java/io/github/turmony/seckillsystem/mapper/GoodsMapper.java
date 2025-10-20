package io.github.turmony.seckillsystem.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.turmony.seckillsystem.entity.Goods;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品Mapper接口
 * 继承BaseMapper，自动拥有CRUD方法
 */
@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {
}
