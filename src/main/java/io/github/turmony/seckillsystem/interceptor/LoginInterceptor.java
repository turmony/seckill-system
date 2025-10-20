package io.github.turmony.seckillsystem.interceptor;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录拦截器
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // ⭐ 添加这段日志
        String requestURI = request.getRequestURI();
        System.out.println("========================================");
        System.out.println("🔍 拦截器执行");
        System.out.println("📍 请求路径: " + requestURI);
        System.out.println("📍 请求方法: " + request.getMethod());
        System.out.println("========================================");

        // 1. 从请求头获取Token
        String token = request.getHeader("Authorization");

        // 2. Token为空或格式不正确
        if (token == null || token.isEmpty()) {
            returnError(response, "请先登录");
            return false;
        }

        // 3. 去掉"Bearer "前缀（如果有）
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 4. 验证Token
        boolean isValid = JwtUtil.validateToken(token);
        if (!isValid) {
            returnError(response, "Token无效或已过期，请重新登录");
            return false;
        }

        // 5. 从Token中获取用户信息，存入Request中供后续使用
        Long userId = JwtUtil.getUserIdFromToken(token);
        String username = JwtUtil.getUsernameFromToken(token);
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);

        // 6. 放行
        return true;
    }

    /**
     * 返回错误信息
     */
    private void returnError(HttpServletResponse response, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);

        Result<Void> result = Result.error(401, message);
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(result);

        response.getWriter().write(json);
    }
}
