package io.github.turmony.seckillsystem.common;

import java.lang.annotation.*;

/**
 * 接口签名验证注解
 * 标记在需要进行签名验证的Controller方法上
 *
 * @author turmony
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireSign {

    /**
     * 是否必须验证签名
     * 默认为true，某些场景下可以设置为false临时关闭验证
     */
    boolean required() default true;

    /**
     * 描述信息
     */
    String description() default "";
}