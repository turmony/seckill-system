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
                .addPathPatterns("/**")                  // 拦截所有路径
                .excludePathPatterns(
                        // 用户相关
                        "/user/register",                // 放行注册接口
                        "/user/login",                   // 放行登录接口
                        "/api/user/register",
                        "/api/user/login",

                        // 商品相关（无需登录即可查看）
                        "/goods/list",                   // 放行商品列表
                        "/goods/detail/**",              // 放行商品详情
                        "/api/goods/list",
                        "/api/goods/detail/**",

                        // 秒杀相关（无需登录即可查看列表和详情）
                        "/seckill/list",                 // 放行秒杀商品列表
                        "/seckill/detail/**",            // 放行秒杀商品详情
                        "/api/seckill/list",
                        "/api/seckill/detail/**",

                        // 管理接口（可根据需要添加权限控制）
                        "/admin/**",

                        // Swagger等（如果有）
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );

        // 注册签名验证拦截器
        registry.addInterceptor(signInterceptor)
                .addPathPatterns("/**")                  // 拦截所有路径
                .excludePathPatterns(
                        "/error",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        // 签名相关接口不需要验证签名（用于生成签名和测试）
                        "/api/sign/**",
                        "/api/test/noSign"
                );

        System.out.println("✅ 登录拦截器已注册");
        System.out.println("✅ 签名验证拦截器已注册");
        System.out.println("========================================");
    }
}