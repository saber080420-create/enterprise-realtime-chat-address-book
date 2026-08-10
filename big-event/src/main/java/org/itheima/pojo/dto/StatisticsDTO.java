package org.itheima.pojo.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统计数据传输对象
 * 用于系统数据统计和展示
 */
@Data
public class StatisticsDTO {
    /**
     * 用户总数
     */
    private Integer totalUsers;
    
    /**
     * 在线用户数
     */
    private Integer onlineUsers;
    
    /**
     * 部门总数
     */
    private Integer totalDepartments;
    
    /**
     * 消息总数
     */
    private Long totalMessages;
    
    /**
     * 群组总数
     */
    private Integer totalGroups;
    
    /**
     * 公告总数
     */
    private Integer totalAnnouncements;
    
    /**
     * 各部门人数统计
     */
    private List<Map<String, Object>> departmentUserStats;
    
    /**
     * 消息类型统计
     */
    private List<Map<String, Object>> messageTypeStats;
    
    /**
     * 每日消息数量统计
     */
    private List<Map<String, Object>> dailyMessageStats;
    
    /**
     * 每日活跃用户统计
     */
    private List<Map<String, Object>> dailyActiveUserStats;
    
    /**
     * 公告阅读率统计
     */
    private List<Map<String, Object>> announcementReadStats;
}