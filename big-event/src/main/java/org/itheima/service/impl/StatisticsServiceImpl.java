package org.itheima.service.impl;

import org.itheima.mapper.AnnouncementMapper;
import org.itheima.mapper.DepartmentMapper;
import org.itheima.mapper.StatisticsMapper;
// import org.itheima.mapper.UserMapper;
import org.itheima.pojo.Announcement;
import org.itheima.pojo.Department;
import org.itheima.pojo.dto.StatisticsDTO;
import org.itheima.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// import java.time.LocalDateTime;
// import java.time.ZoneId;
import java.util.*;

/**
 * 统计服务实现类
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private StatisticsMapper statisticsMapper;
    
    // private final UserMapper userMapper; // 如需使用用户统计，请按需注入
    
    @Autowired
    private AnnouncementMapper announcementMapper;
    
    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    public Map<String, Object> getSystemOverview() {
        Map<String, Object> result = new HashMap<>();
        
        // 获取基础统计数据
        result.put("totalUsers", statisticsMapper.getTotalUsers());
        result.put("onlineUsers", statisticsMapper.getOnlineUsers());
        // 新增：停用用户数
        result.put("disabledUsers", statisticsMapper.getDisabledUsers());
        result.put("totalDepartments", statisticsMapper.getTotalDepartments());
        result.put("totalMessages", statisticsMapper.getTotalMessages());
        result.put("totalGroups", statisticsMapper.getTotalGroups());
        result.put("totalAnnouncements", statisticsMapper.getTotalAnnouncements());
        
        return result;
    }

    @Override
    public List<Map<String, Object>> getDepartmentUserStats() {
        return statisticsMapper.getDepartmentUserStats();
    }

    @Override
    public Map<String, Object> getDepartmentUserStats(Integer departmentId) {
        // 获取部门信息和用户数量
        Map<String, Object> result = new HashMap<>();
        
        // 获取部门信息
        Department department = departmentMapper.findById(departmentId);
        if (department == null) {
            throw new RuntimeException("部门不存在");
        }
        
        // 获取部门用户数量
        Integer userCount = departmentMapper.countUsersByDepartmentId(departmentId);
        
        result.put("id", departmentId);
        result.put("name", department.getDepartmentName());
        result.put("userCount", userCount);
        
        return result;
    }

    @Override
    public List<Map<String, Object>> getMessageTypeStats() {
        return statisticsMapper.getMessageTypeStats();
    }

    @Override
    public List<Map<String, Object>> getDailyMessageStats(Integer days) {
        // 计算开始日期和结束日期
        Date endDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(endDate);
        calendar.add(Calendar.DAY_OF_MONTH, -days);
        Date startDate = calendar.getTime();
        
        return statisticsMapper.getDailyMessageStats(startDate, endDate);
    }

    @Override
    public List<Map<String, Object>> getDailyActiveUserStats(Integer days) {
        // 计算开始日期和结束日期
        Date endDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(endDate);
        calendar.add(Calendar.DAY_OF_MONTH, -days);
        Date startDate = calendar.getTime();
        
        return statisticsMapper.getDailyActiveUserStats(startDate, endDate);
    }

    @Override
    public List<Map<String, Object>> getAnnouncementReadStats() {
        return statisticsMapper.getAnnouncementReadStats();
    }

    @Override
    public List<Map<String, Object>> getDepartmentAnnouncementReadStats(Integer departmentId) {
        // 获取所有公告阅读统计
        List<Map<String, Object>> allStats = statisticsMapper.getAnnouncementReadStats();
        
        // 过滤出部门相关的公告
        List<Map<String, Object>> departmentStats = new ArrayList<>();
        for (Map<String, Object> stat : allStats) {
            Integer announcementId = (Integer) stat.get("id");
            // 查询公告是否属于该部门
            Announcement announcement = announcementMapper.findById(announcementId);
            if (announcement != null) {
                String type = announcement.getType();
                Integer annDepartmentId = announcement.getDepartmentId();
                
                // 如果是部门公告且属于该部门，或者是公司公告
                if (("department".equals(type) && Objects.equals(annDepartmentId, departmentId)) || "company".equals(type)) {
                    departmentStats.add(stat);
                }
            }
        }
        
        return departmentStats;
    }

    // 已移除：活跃度排名

    @Override
    public StatisticsDTO getStatistics(Date startDate, Date endDate) {
        // 获取基础统计数据
        StatisticsDTO statistics = statisticsMapper.getStatistics(startDate, endDate);
        
        // 获取各部门人数统计
        statistics.setDepartmentUserStats(statisticsMapper.getDepartmentUserStats());
        
        // 获取消息类型统计
        statistics.setMessageTypeStats(statisticsMapper.getMessageTypeStats());
        
        // 获取每日消息数量统计
        statistics.setDailyMessageStats(statisticsMapper.getDailyMessageStats(startDate, endDate));
        
        // 获取每日活跃用户统计
        statistics.setDailyActiveUserStats(statisticsMapper.getDailyActiveUserStats(startDate, endDate));
        
        // 获取公告阅读率统计
        statistics.setAnnouncementReadStats(statisticsMapper.getAnnouncementReadStats());
        
        return statistics;
    }
}