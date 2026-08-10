package org.itheima.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import org.itheima.pojo.Result;
import org.itheima.pojo.User;
import org.itheima.service.UserActivityService;
import org.itheima.service.UserService;
import org.itheima.utils.JwtUtil;
import org.itheima.utils.Md5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AccountController（精简门面映射）
 * 
 * 目标：在不改变现有功能的情况下，新增 /account 命名空间的同等接口。
 * 说明：此控制器实现与 /user 下对应接口等价的登录/注册逻辑，便于后续收敛前端调用。
 */
@RestController
@RequestMapping("/account")
@Validated
public class AccountController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 注册（等价于 /user/register）
     */
    @PostMapping("/register")
    public Result<Void> register(
            @Pattern(regexp = "^\\S{5,16}$") String username,
            @Pattern(regexp = "^\\S{5,16}$") String password,
            Integer departmentId
    ){
        User u = userService.findByUserName(username);
        if (u == null){
            userService.register(username, password, departmentId);
            return Result.success();
        } else {
            return Result.error("用户名已被占用");
        }
    }

    /**
     * 登录（等价于 /user/login）
     * 返回 JWT，维持与现有 Redis SSO 的完全一致行为。
     */
    @PostMapping("/login")
    public Result<String> login(
            @Pattern(regexp = "^\\S{5,16}$") String username,
            @Pattern(regexp = "^\\S{5,16}$") String password
    ){
        User loginuser = userService.findByUserName(username);
        if (loginuser == null){
            return Result.error("用户名错误");
        }

        if ("inactive".equals(loginuser.getStatus())) {
            return Result.error("账号已被禁用，请联系系统管理员");
        }

        if (Md5Util.getMD5String(password).equals(loginuser.getPassword())){
            Map<String,Object> claims = new HashMap<>();
            claims.put("id", loginuser.getId());
            claims.put("username", loginuser.getUsername());
            claims.put("role", loginuser.getRole());
            claims.put("departmentId", loginuser.getDepartmentId());
            String token = JwtUtil.genToken(claims);

            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            String userKey = "user_token:" + loginuser.getId();
            try {
                String oldToken = operations.get(userKey);
                if (oldToken != null && !oldToken.isEmpty()) {
                    stringRedisTemplate.delete(oldToken);
                }
            } catch (Exception ignored) {}

            operations.set(token, token, 1, TimeUnit.HOURS);
            operations.set(userKey, token, 1, TimeUnit.HOURS);

            try {
                HttpServletRequest request = null;
                try {
                    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attrs != null) {
                        request = attrs.getRequest();
                    }
                } catch (Exception ignore) {}
                String ipAddress = request != null ? getClientIpAddress(request) : "unknown";
                String deviceInfo = request != null ? request.getHeader("User-Agent") : null;
                userActivityService.recordLogin(loginuser.getId(), ipAddress, deviceInfo);
            } catch (Exception e) {
                System.err.println("记录登录活动失败: " + e.getMessage());
            }
            return Result.success(token);
        }
        return Result.error("密码错误");
    }
}


