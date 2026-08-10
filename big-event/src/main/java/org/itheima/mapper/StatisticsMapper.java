package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.dto.StatisticsDTO;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 统计数据Mapper接口
 */
@Mapper
public interface StatisticsMapper {
    
    /**
     * 获取系统总用户数
     * 
     * @return 总用户数
     */
    @Select("select count(*) from user")
    Integer getTotalUsers();
    
    /**
     * 获取停用用户数
     * 
     * @return 停用用户数（status='inactive'）
     */
    @Select("select count(*) from user where status='inactive'")
    Integer getDisabledUsers();
    
    /**
     * 获取当前在线用户数
     * 
     * @return 在线用户数
     */
    // 在线用户：以最近5分钟有活跃视为在线，避免 online 标记滞留
    @Select("select count(distinct user_id) from user_activity where coalesce(last_active_time, login_time) >= now() - interval 5 minute")
    Integer getOnlineUsers();
    
    /**
     * 获取系统总部门数
     * 
     * @return 总部门数
     */
    @Select("select count(*) from department")
    Integer getTotalDepartments();
    
    /**
     * 获取系统总消息数
     * 
     * @return 总消息数
     */
    @Select("select count(*) from chat_message")
    Integer getTotalMessages();
    
    /**
     * 获取系统总群组数
     * 
     * @return 总群组数
     */
    @Select("select count(*) from chat_group")
    Integer getTotalGroups();
    
    /**
     * 获取系统总公告数
     * 
     * @return 总公告数
     */
    @Select("select count(*) from announcement")
    Integer getTotalAnnouncements();
    
    /**
     * 获取各部门用户数量统计
     * 
     * @return 部门用户数量统计列表
     */
    @Select("select d.id, d.department_name as name, count(u.id) as count " +
            "from department d " +
            "left join user u on d.id = u.department_id and u.status = 'active' " +
            "group by d.id, d.department_name " +
            "order by count desc")
    List<Map<String, Object>> getDepartmentUserStats();
    
    /**
     * 获取消息类型统计
     * 
     * @return 消息类型统计列表
     */
    @Select("select message_type as type, count(*) as count " +
            "from chat_message " +
            "group by message_type " +
            "order by count desc")
    List<Map<String, Object>> getMessageTypeStats();
    
    /**
     * 获取指定时间段内的每日消息统计
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 每日消息统计列表
     */
    @Select("select date(send_time) as date, count(*) as count " +
            "from chat_message " +
            "where send_time between #{startDate} and #{endDate} " +
            "group by date(send_time) " +
            "order by date")
    List<Map<String, Object>> getDailyMessageStats(Date startDate, Date endDate);
    
    /**
     * 获取指定时间段内的每日活跃用户统计
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 每日活跃用户统计列表
     */
    @Select("select date(coalesce(last_active_time, login_time)) as date, count(distinct user_id) as count " +
            "from user_activity " +
            "where coalesce(last_active_time, login_time) between #{startDate} and #{endDate} " +
            "group by date(coalesce(last_active_time, login_time)) " +
            "order by date")
    List<Map<String, Object>> getDailyActiveUserStats(Date startDate, Date endDate);
    
    /**
     * 获取公告阅读统计
     * 
     * @return 公告阅读统计列表
     */
    @Select("select a.id, a.title, " +
            "(select count(*) from announcement_read_status where announcement_id = a.id and is_read = true) as read_count, " +
            "(case when a.type = 'department' and a.department_id is not null then " +
            " (select count(*) from user where status='active' and department_id = a.department_id) " +
            " else (select count(*) from user where status='active') end) as total_count " +
            "from announcement a " +
            "where a.status = 'published' " +
            "order by a.publish_time desc")
    List<Map<String, Object>> getAnnouncementReadStats();
    
    // 已移除：用户活跃度排名（未在前端使用）
    
    // 已移除：群组活跃度排名（未在前端使用）
    
    /**
     * 获取完整的统计数据
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 统计数据对象
     */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM user WHERE status='active') AS total_users, " +
            "(SELECT COUNT(DISTINCT user_id) FROM user_activity WHERE coalesce(last_active_time, login_time) >= now() - interval 5 minute) AS online_users, " +
            "(SELECT COUNT(*) FROM department) AS total_departments, " +
            "(SELECT COUNT(*) FROM chat_message) AS total_messages, " +
            "(SELECT COUNT(*) FROM chat_group) AS total_groups, " +
            "(SELECT COUNT(*) FROM announcement) AS total_announcements")
    StatisticsDTO getStatistics(Date startDate, Date endDate);
}