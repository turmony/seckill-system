package io.github.turmony.seckillsystem.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录返回VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * JWT Token
     */
    private String token;
}
