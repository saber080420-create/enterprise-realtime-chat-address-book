package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.UserActivity;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用户活动Mapper接口
 */
@Mapper
public interface UserActivityMapper {
    
    /**
     * 根据ID查询用户活动
     * 
     * @param id 活动ID
     * @return 用户活动信息
     */
    @Select("select * from user_activity where id = #{id}")
    UserActivity findById(Integer id);
    
    /**
     * 查询用户的所有活动记录
     * 
     * @param userId 用户ID
     * @return 活动记录列表
     */
    @Select("select * from user_activity where user_id = #{userId} order by login_time desc")
    List<UserActivity> findByUserId(Integer userId);
    
    /**
     * 查询用户的活动记录（带分页）
     * 
     * @param userId 用户ID
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 分页后的活动记录列表
     */
    @Select("select * from user_activity where user_id = #{userId} order by login_time desc limit #{limit} offset #{offset}")
    List<UserActivity> findByUserIdWithPaging(Integer userId, Integer limit, Integer offset);
    
    /**
     * 查询用户最近的活动记录
     * 
     * @param userId 用户ID
     * @return 最近的活动记录
     */
    @Select("select * from user_activity where user_id = #{userId} order by login_time desc limit 1")
    UserActivity findLatestByUserId(Integer userId);
    
    /**
     * 添加用户活动记录
     * 
     * @param userActivity 用户活动信息
     */
    @Insert("insert into user_activity(user_id, login_time, ip_address, device_info, online, last_active_time, create_time, update_time) " +
            "values(#{userId}, #{loginTime}, #{ipAddress}, #{deviceInfo}, #{online}, #{lastActiveTime}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(UserActivity userActivity);
    
    /**
     * 更新用户活动记录
     * 
     * @param userActivity 用户活动信息
     */
    @Update("update user_activity set logout_time = #{logoutTime}, message_count = #{messageCount}, " +
            "online = #{online}, last_active_time = #{lastActiveTime}, update_time = now() where id = #{id}")
    void update(UserActivity userActivity);
    
    /**
     * 删除用户活动记录
     * 
     * @param id 活动ID
     */
    @Delete("delete from user_activity where id = #{id}")
    void deleteById(Integer id);
    
    /**
     * 删除用户的所有活动记录
     * 
     * @param userId 用户ID
     */
    @Delete("delete from user_activity where user_id = #{userId}")
    void deleteByUserId(Integer userId);
    
    /**
     * 更新用户在线状态
     * 
     * @param userId 用户ID
     * @param online 是否在线
     * @param lastActiveTime 最后活跃时间
     */
    @Update("update user_activity set online = #{online}, last_active_time = #{lastActiveTime}, " +
            "update_time = now() where user_id = #{userId} and online = true")
    void updateOnlineStatus(Integer userId, Boolean online, Date lastActiveTime);
    
    /**
     * 更新用户登出时间
     * 
     * @param userId 用户ID
     * @param logoutTime 登出时间
     */
    @Update("update user_activity set logout_time = #{logoutTime}, online = false, " +
            "update_time = now() where user_id = #{userId} and online = true")
    void updateLogoutTime(Integer userId, Date logoutTime);
    
    /**
     * 增加用户消息计数
     * 
     * @param userId 用户ID
     */
    @Update("update user_activity set message_count = message_count + 1, " +
            "update_time = now() where user_id = #{userId} and online = true")
    void incrementMessageCount(Integer userId);
    
    /**
     * 查询当前在线用户数量（统一口径：近5分钟内有活跃视为在线）
     * 
     * @return 在线用户数量
     */
    @Select("select count(distinct user_id) from user_activity where coalesce(last_active_time, login_time) >= now() - interval 5 minute")
    Integer countOnlineUsers();
    
    /**
     * 查询所有在线用户
     * 
     * @return 在线用户列表
     */
    @Select("select distinct ua.user_id, u.username, u.nickname, u.user_pic, ua.last_active_time " +
            "from user_activity ua " +
            "join user u on ua.user_id = u.id " +
            "where ua.online = true")
    List<Map<String, Object>> findAllOnlineUsers();
    
    /**
     * 查询指定时间段内的活跃用户统计
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 每日活跃用户数统计
     */
    @Select("select date(login_time) as date, count(distinct user_id) as count " +
            "from user_activity " +
            "where login_time between #{startDate} and #{endDate} " +
            "group by date(login_time) " +
            "order by date")
    List<Map<String, Object>> getDailyActiveUserStats(Date startDate, Date endDate);
    
    /**
     * 查询用户最后活跃时间
     * 
     * @param userId 用户ID
     * @return 最后活跃时间
     */
    @Select("select last_active_time from user_activity where user_id = #{userId} and online = true")
    Date getLastActiveTime(Integer userId);
    
    /**
     * 查询用户是否在线
     * 
     * @param userId 用户ID
     * @return 是否在线
     */
    @Select("select count(*) > 0 from user_activity where user_id = #{userId} and online = true")
    Boolean isUserOnline(Integer userId);
    
    /**
     * 统计指定时间范围内的活跃用户数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 活跃用户数
     */
    @Select("select count(distinct user_id) from user_activity where login_time between #{startTime} and #{endTime}")
    Integer countActiveUsersByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 统计指定时间范围内的登录次数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 登录次数
     */
    @Select("select count(*) from user_activity where login_time between #{startTime} and #{endTime}")
    Integer countLoginsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 获取指定时间范围内的平均在线时长（分钟）
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 平均在线时长（分钟）
     */
    @Select("select avg(timestampdiff(minute, login_time, ifnull(logout_time, now()))) " +
            "from user_activity where login_time between #{startTime} and #{endTime} " +
            "and logout_time is not null")
    Integer getAvgOnlineTimeByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 统计指定时间范围内的消息数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 消息数
     */
    @Select("select sum(message_count) from user_activity where login_time between #{startTime} and #{endTime}")
    Integer countMessagesByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 获取各部门在线时长统计数据
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 部门在线时长统计数据
     */
    @Select("SELECT d.id as department_id, d.department_name as department_name, "
            + "COALESCE(AVG(CASE WHEN DAYOFWEEK(ua.login_time) BETWEEN 2 AND 6 THEN TIMESTAMPDIFF(MINUTE, ua.login_time, IFNULL(ua.logout_time, NOW()))/60 ELSE 0 END), 0) as workday_hours, "
            + "COALESCE(AVG(CASE WHEN DAYOFWEEK(ua.login_time) IN (1, 7) THEN TIMESTAMPDIFF(MINUTE, ua.login_time, IFNULL(ua.logout_time, NOW()))/60 ELSE 0 END), 0) as weekend_hours "
            + "FROM department d "
            + "LEFT JOIN user u ON d.id = u.department_id "
            + "LEFT JOIN user_activity ua ON u.id = ua.user_id AND ua.login_time BETWEEN #{startTime} AND #{endTime} "
            + "GROUP BY d.id, d.department_name "
            + "ORDER BY d.id")
    List<Map<String, Object>> getDepartmentOnlineTimeStats(LocalDateTime startTime, LocalDateTime endTime);
}