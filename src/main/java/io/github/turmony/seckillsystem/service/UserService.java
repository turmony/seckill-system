package io.github.turmony.seckillsystem.service;


import io.github.turmony.seckillsystem.dto.UserLoginDTO;
import io.github.turmony.seckillsystem.dto.UserRegisterDTO;
import io.github.turmony.seckillsystem.vo.UserLoginVO;


/**
 * 用户Service接口
 */
public interface UserService {

    /**
     * 用户注册
     * @param registerDTO 注册信息
     */
    void register(UserRegisterDTO registerDTO);

    /**
     * 用户登录
     * @param loginDTO 登录信息
     * @return 登录结果（包含Token）
     */
    UserLoginVO login(UserLoginDTO loginDTO);
}
