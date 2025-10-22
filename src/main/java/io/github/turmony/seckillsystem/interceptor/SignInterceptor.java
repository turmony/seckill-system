package io.github.turmony.seckillsystem.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turmony.seckillsystem.common.RequireSign;
import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.util.SignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * 签名验证拦截器
 * 拦截标记了@RequireSign注解的接口，验证请求签名
 *
 * @author turmony
 */
@Slf4j
@Component
public class SignInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只拦截Controller方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        // 检查方法是否有@RequireSign注解
        RequireSign requireSign = handlerMethod.getMethodAnnotation(RequireSign.class);

        // 没有注解或者不需要验证，直接放行
        if (requireSign == null || !requireSign.required()) {
            return true;
        }

        // 获取请求参数
        Map<String, String> params = getRequestParams(request);

        log.info("接口签名验证开始, URI={}, 参数={}", request.getRequestURI(), params);

        // 验证签名
        boolean isValid = SignUtil.verifySign(params);

        if (!isValid) {
            // 签名验证失败，返回错误信息
            log.warn("接口签名验证失败, URI={}", request.getRequestURI());
            responseError(response, "签名验证失败");
            return false;
        }

        log.info("接口签名验证成功, URI={}", request.getRequestURI());
        return true;
    }

    /**
     * 获取请求参数
     */
    private Map<String, String> getRequestParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();

        // 获取所有参数
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            // 只取第一个值
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        }

        return params;
    }

    /**
     * 返回错误信息
     */
    private void responseError(HttpServletResponse response, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        Result<Object> result = Result.error(message);
        String jsonResult = objectMapper.writeValueAsString(result);

        PrintWriter writer = response.getWriter();
        writer.write(jsonResult);
        writer.flush();
        writer.close();
    }
}