package io.github.turmony.seckillsystem.controller;


import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.dto.UserLoginDTO;
import io.github.turmony.seckillsystem.dto.UserRegisterDTO;
import io.github.turmony.seckillsystem.service.UserService;
import io.github.turmony.seckillsystem.vo.UserLoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户Controller
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册
     * @param registerDTO 注册信息
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody UserRegisterDTO registerDTO) {
        try {
            userService.register(registerDTO);
            return Result.success("注册成功", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 用户登录
     * @param loginDTO 登录信息
     * @return 登录结果（包含Token）
     */
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Validated @RequestBody UserLoginDTO loginDTO) {
        try {
            UserLoginVO loginVO = userService.login(loginDTO);
            return Result.success("登录成功", loginVO);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 测试接口（需要登录才能访问）
     * @return 测试结果
     */
    @GetMapping("/test")
    public Result<String> test() {
        return Result.success("这是一个需要登录才能访问的接口");
    }
}
