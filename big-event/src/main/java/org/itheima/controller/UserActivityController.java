package org.itheima.controller;

import org.itheima.pojo.Result;
import org.itheima.pojo.User;
import org.itheima.pojo.UserActivity;
import org.itheima.service.UserActivityService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 用户活动控制器
 */
@RestController
@RequestMapping("/user/activity")
@Validated
public class UserActivityController {

    /**
     * 获取客户端IP地址
     * 
     * @param request HTTP请求
     * @return 客户端IP地址
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

    @Autowired
    private UserActivityService userActivityService;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 记录用户登录
     * 
     * @return 记录结果
     */
    @PostMapping("/login")
    public Result<UserActivity> recordLogin(HttpServletRequest request) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            // 获取客户端IP地址
            String ipAddress = getClientIpAddress(request);
            
            // 获取设备信息（User-Agent）
            String deviceInfo = request.getHeader("User-Agent");
            
            UserActivity activity = userActivityService.recordLogin(userId, ipAddress, deviceInfo);
            return Result.success(activity);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 记录用户登出
     * 
     * @param token 用户token
     * @return 记录结果
     */
    @PostMapping("/logout")
    public Result<UserActivity> recordLogout(@RequestHeader(value = "Authorization", required = false) String token) {
        try {
            System.out.println("=== 登出处理开始 ====");
            System.out.println("收到登出请求，Authorization: " + (token != null ? "已提供" : "未提供"));
            
            // 检查token是否为空
            if (token == null || token.isEmpty()) {
                System.err.println("错误: 请求头中没有Authorization token");
                return Result.error("未提供认证令牌");
            }
            
            try {
                // 获取当前登录用户ID
                Map<String, Object> claims = ThreadLocalUtil.get();
                if (claims == null) {
                    System.err.println("错误: ThreadLocal中没有用户信息");
                    return Result.error("无法获取用户信息");
                }
                
                Integer userId = (Integer) claims.get("id");
                if (userId == null) {
                    System.err.println("错误: 无法获取用户ID");
                    return Result.error("无法获取用户ID");
                }
                
                System.out.println("当前用户ID: " + userId);
                
                // 记录用户登出活动
                System.out.println("开始记录用户登出活动");
                UserActivity activity = userActivityService.recordLogout(userId);
                System.out.println("用户登出活动记录成功: " + activity);
                
                // 从Redis中删除token，使其立即失效
                System.out.println("开始从Redis中删除token");
                ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
                Boolean deleted = operations.getOperations().delete(token);
                System.out.println("Redis删除token结果: " + deleted);
                
                System.out.println("=== 登出处理完成 ====");
                return Result.success(activity);
            } catch (Exception e) {
                System.err.println("登出处理内部异常: " + e.getMessage());
                e.printStackTrace();
                return Result.error("登出处理失败: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("登出处理外部异常: " + e.getMessage());
            e.printStackTrace();
            return Result.error("登出处理失败");
        }
    }

    /**
     * 更新用户在线状态
     * 
     * @param isOnline 是否在线
     * @return 更新结果
     */
    @PutMapping("/online-status")
    public Result<UserActivity> updateOnlineStatus(@RequestParam Boolean isOnline) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            UserActivity activity = userActivityService.updateOnlineStatus(userId, isOnline);
            return Result.success(activity);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 增加用户消息计数
     * 
     * @param count 增加数量
     * @return 更新后的用户活动信息
     */
    @PutMapping("/message-count")
    public Result<UserActivity> incrementMessageCount(@RequestParam Integer count) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            UserActivity activity = userActivityService.incrementMessageCount(userId, count);
            return Result.success(activity);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取在线用户数量
     * 
     * @return 在线用户数量
     */
    @GetMapping("/online-count")
    public Result<Integer> getOnlineUserCount() {
        int count = userActivityService.countOnlineUsers();
        return Result.success(count);
    }

    /**
     * 获取在线用户列表
     * 
     * @return 在线用户列表
     */
    @GetMapping("/online-users")
    public Result<List<User>> getOnlineUsers() {
        List<User> users = userActivityService.findAllOnlineUsers();
        return Result.success(users);
    }

    /**
     * 获取活跃用户统计
     * 
     * @param days 天数
     * @return 活跃用户统计
     */
    @GetMapping("/active-stats")
    public Result<Map<String, Object>> getActiveUserStats(@RequestParam(defaultValue = "7") Integer days) {
        Map<String, Object> stats = userActivityService.getActiveUserStats(days);
        return Result.success(stats);
    }

    /**
     * 获取用户最后活跃时间
     * 
     * @param userId 用户ID
     * @return 最后活跃时间
     */
    @GetMapping("/last-active/{userId}")
    public Result<Map<String, Object>> getLastActiveTime(@PathVariable Integer userId) {
        try {
            Map<String, Object> result = userActivityService.getLastActiveTimeAndOnlineStatus(userId);
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取当前用户活动信息
     * 
     * @return 用户活动信息
     */
    @GetMapping("/current")
    public Result<UserActivity> getCurrentUserActivity() {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            UserActivity activity = userActivityService.findLatestByUserId(userId);
            return Result.success(activity);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 获取部门在线时长统计数据
     * 
     * @param days 天数
     * @return 部门在线时长统计数据
     */
    @GetMapping("/department/online-time")
    public Result<List<Map<String, Object>>> getDepartmentOnlineTimeStats(@RequestParam(defaultValue = "30") Integer days) {
        try {
            List<Map<String, Object>> stats = userActivityService.getDepartmentOnlineTimeStats(days);
            // 即使是空列表也返回成功，前端会处理空数据的显示
            return Result.success(stats);
        } catch (Exception e) {
            // 记录异常但返回空列表，避免前端显示错误
            System.err.println("控制器获取部门在线时长统计数据失败: " + e.getMessage());
            e.printStackTrace();
            // 返回空列表而不是错误信息
            return Result.success(List.of());
        }
    }
}
