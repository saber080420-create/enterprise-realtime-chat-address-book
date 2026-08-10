package org.itheima.controller;

import org.itheima.pojo.Result;
import org.itheima.pojo.dto.StatisticsDTO;
import org.itheima.service.StatisticsService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 统计数据控制器
 */
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    /**
     * 获取系统概览统计数据
     * 
     * @return 系统概览统计数据
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getSystemOverview() {
        // 获取当前登录用户信息
        Map<String, Object> claims = ThreadLocalUtil.get();
        String userRole = (String) claims.get("role");
        
        // 检查权限：只有系统管理员可以查看全部统计数据
        if (!"system_admin".equals(userRole)) {
            return Result.error("权限不足，只有系统管理员可以查看全部统计数据");
        }
        
        Map<String, Object> overview = statisticsService.getSystemOverview();
        return Result.success(overview);
    }

    /**
     * 获取部门用户统计数据
     * 
     * @return 部门用户统计数据
     */
    @GetMapping("/department/users")
    public Result<List<Map<String, Object>>> getDepartmentUserStats() {
        // 获取当前登录用户信息
        Map<String, Object> claims = ThreadLocalUtil.get();
        String userRole = (String) claims.get("role");
        Integer departmentId = (Integer) claims.get("departmentId");
        
        // 根据用户角色确定查询范围
        List<Map<String, Object>> stats;
        if ("system_admin".equals(userRole)) {
            // 系统管理员可以查看所有部门的统计数据
            stats = statisticsService.getDepartmentUserStats();
        } else if ("department_admin".equals(userRole)) {
            // 部门管理员只能查看自己部门的统计数据
            // 这里简化处理，返回所有部门数据，前端根据部门ID筛选
            stats = statisticsService.getDepartmentUserStats();
        } else {
            // 普通员工无权查看
            return Result.error("权限不足，只有管理员可以查看部门用户统计数据");
        }
        
        return Result.success(stats);
    }

    /**
     * 获取消息类型统计数据
     * 
     * @return 消息类型统计数据
     */
    @GetMapping("/messages/types")
    public Result<List<Map<String, Object>>> getMessageTypeStats() {
        // 获取当前登录用户信息
        Map<String, Object> claims = ThreadLocalUtil.get();
        String userRole = (String) claims.get("role");
        
        // 检查权限：只有系统管理员和部门管理员可以查看消息类型统计数据
        if (!"system_admin".equals(userRole) && !"department_admin".equals(userRole)) {
            return Result.error("权限不足，只有管理员可以查看消息类型统计数据");
        }
        
        List<Map<String, Object>> stats = statisticsService.getMessageTypeStats();
        return Result.success(stats);
    }

    /**
     * 获取每日消息统计数据
     * 
     * @param days 天数
     * @return 每日消息统计数据
     */
    @GetMapping("/messages/daily")
    public Result<List<Map<String, Object>>> getDailyMessageStats(
            @RequestParam(defaultValue = "7") Integer days) {
        // 获取当前登录用户信息
        Map<String, Object> claims = ThreadLocalUtil.get();
        String userRole = (String) claims.get("role");
        
        // 检查权限：只有系统管理员和部门管理员可以查看每日消息统计数据
        if (!"system_admin".equals(userRole) && !"department_admin".equals(userRole)) {
            return Result.error("权限不足，只有管理员可以查看每日消息统计数据");
        }
        
        List<Map<String, Object>> stats = statisticsService.getDailyMessageStats(days);
        return Result.success(stats);
    }

    /**
     * 获取每日活跃用户统计数据
     * 
     * @param days 天数
     * @return 每日活跃用户统计数据
     */
    @GetMapping("/users/active/daily")
    public Result<List<Map<String, Object>>> getDailyActiveUserStats(
            @RequestParam(defaultValue = "7") Integer days) {
        // 获取当前登录用户信息
        Map<String, Object> claims = ThreadLocalUtil.get();
        String userRole = (String) claims.get("role");
        
        // 检查权限：只有系统管理员和部门管理员可以查看每日活跃用户统计数据
        if (!"system_admin".equals(userRole) && !"department_admin".equals(userRole)) {
            return Result.error("权限不足，只有管理员可以查看每日活跃用户统计数据");
        }
        
        List<Map<String, Object>> stats = statisticsService.getDailyActiveUserStats(days);
        return Result.success(stats);
    }

    /**
     * 获取公告阅读统计数据
     * 
     * @return 公告阅读统计数据
     */
    @GetMapping("/announcements/read")
    public Result<List<Map<String, Object>>> getAnnouncementReadStats() {
        // 获取当前登录用户信息
        Map<String, Object> claims = ThreadLocalUtil.get();
        String userRole = (String) claims.get("role");
        Integer departmentId = (Integer) claims.get("departmentId");
        
        // 根据用户角色确定查询范围
        List<Map<String, Object>> stats;
        if ("system_admin".equals(userRole)) {
            // 系统管理员可以查看所有公告的阅读统计
            stats = statisticsService.getAnnouncementReadStats();
        } else if ("department_admin".equals(userRole)) {
            // 部门管理员只能查看自己部门的公告阅读统计
            stats = statisticsService.getDepartmentAnnouncementReadStats(departmentId);
        } else {
            // 普通员工无权查看
            return Result.error("权限不足，只有管理员可以查看公告阅读统计数据");
        }
        
        return Result.success(stats);
    }

    // 已移除：用户活跃度排名接口（未在前端使用）

    // 已移除：群组活跃度排名接口（未在前端使用）

    /**
     * 获取完整的统计数据
     * 
     * @param startDate 开始日期（格式：yyyy-MM-dd）
     * @param endDate 结束日期（格式：yyyy-MM-dd）
     * @return 统计数据对象
     */
    @GetMapping("/full")
    public Result<StatisticsDTO> getFullStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        // 获取当前登录用户信息
        Map<String, Object> claims = ThreadLocalUtil.get();
        String userRole = (String) claims.get("role");
        
        // 检查权限：只有系统管理员可以查看完整统计数据
        if (!"system_admin".equals(userRole)) {
            return Result.error("权限不足，只有系统管理员可以查看完整统计数据");
        }
        
        try {
            // 解析日期
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date start = startDate != null ? dateFormat.parse(startDate) : null;
            Date end = endDate != null ? dateFormat.parse(endDate) : new Date();
            
            // 如果开始日期为空，默认为30天前
            if (start == null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(end);
                calendar.add(Calendar.DAY_OF_MONTH, -30);
                start = calendar.getTime();
            }
            
            StatisticsDTO statistics = statisticsService.getStatistics(start, end);
            return Result.success(statistics);
        } catch (ParseException e) {
            return Result.error("日期格式错误，请使用yyyy-MM-dd格式");
        }
    }
}