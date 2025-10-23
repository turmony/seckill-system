package io.github.turmony.seckillsystem.config;

import io.github.turmony.seckillsystem.interceptor.LoginInterceptor;
import io.github.turmony.seckillsystem.interceptor.SignInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private SignInterceptor signInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        System.out.println("========================================");
        System.out.println("⚙️ WebConfig 正在配置拦截器");
        System.out.println("========================================");

        // 注册登录拦截器
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // ⭐ 错误页面（重要！）
                        "/error",

                        // 用户相关
                        "/user/register/**",
                        "/user/login/**",
                        "/api/user/register/**",
                        "/api/user/login/**",

                        // 商品相关
                        "/goods/list/**",
                        "/goods/detail/**",
                        "/api/goods/list/**",
                        "/api/goods/detail/**",

                        // 秒杀相关
                        "/seckill/list/**",
                        "/seckill/detail/**",
                        "/api/seckill/list/**",
                        "/api/seckill/detail/**",

                        // 测试接口
                        "/test/**",
                        "/api/test/**",

                        // 管理接口
                        "/admin/**",

                        // Swagger
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );

        // 注册签名验证拦截器
        registry.addInterceptor(signInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/error",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",

                        // ===== 新增：测试接口（用于负载均衡验证） =====
                        "/test/**",
                        "/api/test/**",

                        // ===== 新增：基础接口（方便测试） =====
                        "/user/register/**",
                        "/user/login/**",
                        "/api/user/register/**",
                        "/api/user/login/**",

                        "/goods/list/**",
                        "/api/goods/list/**",
                        "/goods/detail/**",
                        "/api/goods/detail/**",

                        "/seckill/list/**",
                        "/api/seckill/list/**",
                        "/seckill/detail/**",
                        "/api/seckill/detail/**",
                        // =========================================

                        "/api/sign/**",
                        "/api/test/noSign"
                );

        System.out.println("✅ 登录拦截器已注册");
        System.out.println("✅ 签名验证拦截器已注册");
        System.out.println("========================================");
    }
}