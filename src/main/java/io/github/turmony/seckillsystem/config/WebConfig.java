package io.github.turmony.seckillsystem.config;


import io.github.turmony.seckillsystem.interceptor.LoginInterceptor;
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")              // 拦截所有/api开头的接口
                .excludePathPatterns(
                        "/api/user/register",            // 放行注册接口
                        "/api/user/login",               // 放行登录接口
                        "/api/goods/list",               // 放行商品列表（后续添加）
                        "/api/seckill/list"              // 放行秒杀列表（后续添加）
                );
    }
}