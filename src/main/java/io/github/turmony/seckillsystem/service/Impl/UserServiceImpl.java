package io.github.turmony.seckillsystem.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.turmony.seckillsystem.dto.UserLoginDTO;
import io.github.turmony.seckillsystem.dto.UserRegisterDTO;
import io.github.turmony.seckillsystem.entity.User;
import io.github.turmony.seckillsystem.mapper.UserMapper;
import io.github.turmony.seckillsystem.service.UserService;
import io.github.turmony.seckillsystem.util.JwtUtil;
import io.github.turmony.seckillsystem.util.MD5Util;
import io.github.turmony.seckillsystem.vo.UserLoginVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/**
 * 用户Service实现类
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 用户注册
     */
    @Override
    public void register(UserRegisterDTO registerDTO) {
        // 1. 检查用户名是否已存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, registerDTO.getUsername());
        User existUser = userMapper.selectOne(queryWrapper);

        if (existUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 创建用户对象
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(MD5Util.encrypt(registerDTO.getPassword())); // MD5加密
        user.setNickname(registerDTO.getNickname());
        user.setPhone(registerDTO.getPhone());
        user.setStatus(0); // 正常状态
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 3. 插入数据库
        int result = userMapper.insert(user);
        if (result != 1) {
            throw new RuntimeException("注册失败");
        }
    }

    /**
     * 用户登录
     */
    @Override
    public UserLoginVO login(UserLoginDTO loginDTO) {
        // 1. 根据用户名查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, loginDTO.getUsername());
        User user = userMapper.selectOne(queryWrapper);

        // 2. 用户不存在
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 3. 验证密码
        boolean isPasswordCorrect = MD5Util.verify(loginDTO.getPassword(), user.getPassword());
        if (!isPasswordCorrect) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 4. 检查用户状态
        if (user.getStatus() != 0) {
            throw new RuntimeException("账号已被禁用");
        }

        // 5. 生成JWT Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());

        // 6. 返回登录结果
        return new UserLoginVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                token
        );
    }
}
