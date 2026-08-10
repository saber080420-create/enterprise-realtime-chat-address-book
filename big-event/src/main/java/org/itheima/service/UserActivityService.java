package org.itheima.service;

import org.itheima.pojo.User;
import org.itheima.pojo.UserActivity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户活动服务接口
 */
public interface UserActivityService {
    
    /**
     * 根据ID查询用户活动
     * 
     * @param id 活动ID
     * @return 用户活动信息
     */
    UserActivity findById(Integer id);
    
    /**
     * 查询用户的所有活动记录
     * 
     * @param userId 用户ID
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 活动记录列表
     */
    List<UserActivity> findByUserId(Integer userId, Integer limit, Integer offset);
    
    /**
     * 查询用户最近的活动记录
     * 
     * @param userId 用户ID
     * @return 最近的活动记录
     */
    UserActivity findLatestByUserId(Integer userId);
    
    /**
     * 添加用户活动记录
     * 
     * @param userActivity 用户活动信息
     * @return 添加的活动记录
     */
    UserActivity add(UserActivity userActivity);
    
    /**
     * 更新用户活动记录
     * 
     * @param userActivity 用户活动信息
     * @return 更新后的活动记录
     */
    UserActivity update(UserActivity userActivity);
    
    /**
     * 删除用户活动记录
     * 
     * @param id 活动ID
     * @return 是否删除成功
     */
    boolean deleteById(Integer id);
    
    /**
     * 记录用户登录
     * 
     * @param userId 用户ID
     * @param ipAddress IP地址
     * @param deviceInfo 设备信息
     * @return 活动记录
     */
    UserActivity recordLogin(Integer userId, String ipAddress, String deviceInfo);
    
    /**
     * 记录用户登出
     * 
     * @param userId 用户ID
     * @return 活动记录
     */
    UserActivity recordLogout(Integer userId);
    
    /**
     * 更新用户在线状态
     * 
     * @param userId 用户ID
     * @param online 是否在线
     * @return 更新后的活动记录
     */
    UserActivity updateOnlineStatus(Integer userId, Boolean online);
    
    /**
     * 更新用户登出时间
     * 
     * @param userId 用户ID
     * @return 更新后的活动记录
     */
    UserActivity updateLogoutTime(Integer userId);
    
    /**
     * 增加用户消息计数
     * 
     * @param userId 用户ID
     * @param count 增加的数量
     * @return 更新后的活动记录
     */
    UserActivity incrementMessageCount(Integer userId, Integer count);
    
    /**
     * 查询当前在线用户数量
     * 
     * @return 在线用户数量
     */
    Integer countOnlineUsers();
    
    /**
     * 查询所有在线用户
     * 
     * @return 在线用户列表
     */
    List<User> findAllOnlineUsers();
    
    /**
     * 查询指定时间段内的活跃用户统计
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 活跃用户统计
     */
    Map<String, Object> getActiveUserStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 查询最近几天的活跃用户统计
     * 
     * @param days 天数
     * @return 活跃用户统计
     */
    Map<String, Object> getActiveUserStats(Integer days);
    
    /**
     * 获取用户最后活跃时间和在线状态
     * 
     * @param userId 用户ID
     * @return 包含最后活跃时间和在线状态的Map
     */
    Map<String, Object> getLastActiveTimeAndOnlineStatus(Integer userId);
    
    /**
     * 查询用户最后活跃时间
     * 
     * @param userId 用户ID
     * @return 最后活跃时间
     */
    LocalDateTime getLastActiveTime(Integer userId);
    
    /**
     * 查询用户是否在线
     * 
     * @param userId 用户ID
     * @return 是否在线
     */
    boolean isUserOnline(Integer userId);
    
    /**
     * 获取部门在线时长统计数据
     * 
     * @param days 天数
     * @return 部门在线时长统计数据
     */
    List<Map<String, Object>> getDepartmentOnlineTimeStats(Integer days);
}