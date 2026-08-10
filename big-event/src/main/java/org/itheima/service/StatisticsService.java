package org.itheima.service;

import org.itheima.pojo.dto.StatisticsDTO;

// import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 统计服务接口
 */
public interface StatisticsService {
    
    /**
     * 获取系统概览统计数据
     * 
     * @return 系统概览统计数据
     */
    Map<String, Object> getSystemOverview();
    
    /**
     * 获取部门用户统计数据
     * 
     * @return 部门用户统计数据
     */
    List<Map<String, Object>> getDepartmentUserStats();
    
    /**
     * 获取指定部门的用户统计数据
     * 
     * @param departmentId 部门ID
     * @return 部门用户统计数据
     */
    Map<String, Object> getDepartmentUserStats(Integer departmentId);
    
    /**
     * 获取消息类型统计数据
     * 
     * @return 消息类型统计数据
     */
    List<Map<String, Object>> getMessageTypeStats();
    
    /**
     * 获取每日消息统计数据
     * 
     * @param days 天数
     * @return 每日消息统计数据
     */
    List<Map<String, Object>> getDailyMessageStats(Integer days);
    
    /**
     * 获取每日活跃用户统计数据
     * 
     * @param days 天数
     * @return 每日活跃用户统计数据
     */
    List<Map<String, Object>> getDailyActiveUserStats(Integer days);
    
    /**
     * 获取公告阅读统计数据
     * 
     * @return 公告阅读统计数据
     */
    List<Map<String, Object>> getAnnouncementReadStats();
    
    /**
     * 获取指定部门的公告阅读统计数据
     * 
     * @param departmentId 部门ID
     * @return 部门公告阅读统计数据
     */
    List<Map<String, Object>> getDepartmentAnnouncementReadStats(Integer departmentId);
    
    // 已移除：活跃度排名（未在前端使用）
    
    /**
     * 获取完整的统计数据
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 统计数据对象
     */
    StatisticsDTO getStatistics(Date startDate, Date endDate);
}