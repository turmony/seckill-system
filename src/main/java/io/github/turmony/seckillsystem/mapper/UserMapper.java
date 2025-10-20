package io.github.turmony.seckillsystem.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.turmony.seckillsystem.entity.User ;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // MyBatis-Plus已经提供了基础的CRUD方法，这里不需要额外定义
}