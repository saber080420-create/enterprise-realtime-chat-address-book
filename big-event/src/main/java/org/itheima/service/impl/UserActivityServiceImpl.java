package org.itheima.service.impl;

import org.itheima.mapper.UserActivityMapper;
import org.itheima.mapper.UserMapper;
import org.itheima.pojo.User;
import org.itheima.pojo.UserActivity;
import org.itheima.service.UserActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户活动服务实现类
 */
@Service
public class UserActivityServiceImpl implements UserActivityService {

    @Autowired
    private UserActivityMapper userActivityMapper;
    
    @Autowired
    private UserMapper userMapper;

    @Override
    public UserActivity findById(Integer id) {
        return userActivityMapper.findById(id);
    }

    @Override
    public List<UserActivity> findByUserId(Integer userId, Integer limit, Integer offset) {
        // 参数处理
        Integer effectiveLimit = limit != null ? limit : Integer.MAX_VALUE;
        Integer effectiveOffset = offset != null ? offset : 0;
        
        // 使用带分页的查询方法
        return userActivityMapper.findByUserIdWithPaging(userId, effectiveLimit, effectiveOffset);
    }

    @Override
    public UserActivity findLatestByUserId(Integer userId) {
        return userActivityMapper.findLatestByUserId(userId);
    }

    @Override
    @Transactional
    public UserActivity add(UserActivity userActivity) {
        // 检查用户是否存在
        User user = userMapper.findById(userActivity.getUserId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        userActivity.setCreateTime(now);
        userActivity.setUpdateTime(now);
        
        // 如果未设置最后活跃时间，则设置为当前时间
        if (userActivity.getLastActiveTime() == null) {
            userActivity.setLastActiveTime(now);
        }
        
        // 保存用户活动
        userActivityMapper.add(userActivity);
        
        return userActivity;
    }

    @Override
    @Transactional
    public UserActivity update(UserActivity userActivity) {
        // 检查用户活动是否存在
        UserActivity existingActivity = userActivityMapper.findById(userActivity.getId());
        if (existingActivity == null) {
            throw new RuntimeException("用户活动记录不存在");
        }
        
        // 更新时间
        userActivity.setUpdateTime(LocalDateTime.now());
        
        // 更新用户活动
        userActivityMapper.update(userActivity);
        
        return userActivity;
    }

    @Override
    @Transactional
    public boolean deleteById(Integer id) {
        // 检查用户活动是否存在
        UserActivity existingActivity = userActivityMapper.findById(id);
        if (existingActivity == null) {
            throw new RuntimeException("用户活动记录不存在");
        }
        
        // 删除用户活动
        userActivityMapper.deleteById(id);
        
        return true;
    }

    @Override
    @Transactional
    public UserActivity recordLogin(Integer userId, String ipAddress, String deviceInfo) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 查找用户最近的活动记录
        UserActivity latestActivity = userActivityMapper.findLatestByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        
        // 如果存在最近的活动记录且在线状态为true，则更新该记录
        if (latestActivity != null && Boolean.TRUE.equals(latestActivity.getOnline())) {
            latestActivity.setIpAddress(ipAddress);
            latestActivity.setDeviceInfo(deviceInfo);
            latestActivity.setLastActiveTime(now);
            latestActivity.setUpdateTime(now);
            userActivityMapper.update(latestActivity);
            return latestActivity;
        }
        
        // 创建新的活动记录
        UserActivity userActivity = new UserActivity();
        userActivity.setUserId(userId);
        userActivity.setLoginTime(now);
        userActivity.setIpAddress(ipAddress);
        userActivity.setDeviceInfo(deviceInfo);
        userActivity.setMessageCount(0);
        userActivity.setOnline(true);
        userActivity.setLastActiveTime(now);
        userActivity.setCreateTime(now);
        userActivity.setUpdateTime(now);
        
        userActivityMapper.add(userActivity);
        
        return userActivity;
    }

    @Override
    @Transactional
    public UserActivity recordLogout(Integer userId) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 查找用户最近的活动记录
        UserActivity latestActivity = userActivityMapper.findLatestByUserId(userId);
        if (latestActivity == null) {
            throw new RuntimeException("用户没有活动记录");
        }
        
        // 更新登出时间和在线状态
        LocalDateTime now = LocalDateTime.now();
        latestActivity.setLogoutTime(now);
        latestActivity.setOnline(false);
        latestActivity.setLastActiveTime(now);
        latestActivity.setUpdateTime(now);
        
        userActivityMapper.update(latestActivity);
        
        return latestActivity;
    }

    @Override
    @Transactional
    public UserActivity updateOnlineStatus(Integer userId, Boolean online) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 查找用户最近的活动记录
        UserActivity latestActivity = userActivityMapper.findLatestByUserId(userId);
        if (latestActivity == null) {
            // 如果没有活动记录且要设置为在线，则创建新记录
            if (Boolean.TRUE.equals(online)) {
                // 获取默认值，避免传递null
                String defaultIp = "0.0.0.0";
                String defaultDevice = "Unknown Device";
                return recordLogin(userId, defaultIp, defaultDevice);
            }
            throw new RuntimeException("用户没有活动记录");
        }
        
        // 更新在线状态
        LocalDateTime now = LocalDateTime.now();
        latestActivity.setOnline(online);
        latestActivity.setLastActiveTime(now);
        latestActivity.setUpdateTime(now);
        
        // 如果设置为离线，更新登出时间
        if (Boolean.FALSE.equals(online)) {
            latestActivity.setLogoutTime(now);
        }
        
        userActivityMapper.update(latestActivity);
        
        return latestActivity;
    }

    @Override
    @Transactional
    public UserActivity updateLogoutTime(Integer userId) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 查找用户最近的活动记录
        UserActivity latestActivity = userActivityMapper.findLatestByUserId(userId);
        if (latestActivity == null) {
            throw new RuntimeException("用户没有活动记录");
        }
        
        // 更新登出时间
        LocalDateTime now = LocalDateTime.now();
        latestActivity.setLogoutTime(now);
        latestActivity.setUpdateTime(now);
        
        userActivityMapper.update(latestActivity);
        
        return latestActivity;
    }

    @Override
    @Transactional
    public UserActivity incrementMessageCount(Integer userId, Integer count) {
        if (count == null || count <= 0) {
            throw new IllegalArgumentException("增加的消息数量必须大于0");
        }
        
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 查找用户最近的活动记录
        UserActivity latestActivity = userActivityMapper.findLatestByUserId(userId);
        if (latestActivity == null) {
            throw new RuntimeException("用户没有活动记录");
        }
        
        // 增加消息计数
        Integer currentCount = latestActivity.getMessageCount();
        if (currentCount == null) {
            currentCount = 0;
        }
        latestActivity.setMessageCount(currentCount + count);
        
        // 更新最后活跃时间
        LocalDateTime now = LocalDateTime.now();
        latestActivity.setLastActiveTime(now);
        latestActivity.setUpdateTime(now);
        
        userActivityMapper.update(latestActivity);
        
        return latestActivity;
    }

    @Override
    public Integer countOnlineUsers() {
        return userActivityMapper.countOnlineUsers();
    }

    @Override
    public List<User> findAllOnlineUsers() {
        // 获取在线用户数据
        List<Map<String, Object>> onlineUsersData = userActivityMapper.findAllOnlineUsers();
        
        // 将Map数据转换为User对象列表
        return onlineUsersData.stream()
            .map(data -> {
                User user = new User();
                user.setId((Integer) data.get("user_id"));
                user.setUsername((String) data.get("username"));
                user.setNickname((String) data.get("nickname"));
                user.setUserPic((String) data.get("user_pic"));
                return user;
            })
            .toList();
    }

    @Override
    public Map<String, Object> getActiveUserStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("开始时间和结束时间不能为空");
        }
        
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        
        // 获取活跃用户统计数据
        Integer totalActiveUsers = userActivityMapper.countActiveUsersByTimeRange(startTime, endTime);
        Integer totalLogins = userActivityMapper.countLoginsByTimeRange(startTime, endTime);
        Integer avgOnlineTime = userActivityMapper.getAvgOnlineTimeByTimeRange(startTime, endTime);
        Integer totalMessages = userActivityMapper.countMessagesByTimeRange(startTime, endTime);
        
        // 构建统计结果
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalActiveUsers", totalActiveUsers);
        stats.put("totalLogins", totalLogins);
        stats.put("avgOnlineTimeMinutes", avgOnlineTime);
        stats.put("totalMessages", totalMessages);
        stats.put("startTime", startTime);
        stats.put("endTime", endTime);
        
        return stats;
    }

    @Override
    public LocalDateTime getLastActiveTime(Integer userId) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 查找用户最近的活动记录
        UserActivity latestActivity = userActivityMapper.findLatestByUserId(userId);
        if (latestActivity == null) {
            return null;
        }
        
        return latestActivity.getLastActiveTime();
    }

    @Override
    public boolean isUserOnline(Integer userId) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 查找用户最近的活动记录
        UserActivity latestActivity = userActivityMapper.findLatestByUserId(userId);
        if (latestActivity == null) {
            return false;
        }
        
        return Boolean.TRUE.equals(latestActivity.getOnline());
    }
    
    @Override
    public List<Map<String, Object>> getDepartmentOnlineTimeStats(Integer days) {
        try {
            // 验证参数
            if (days == null || days <= 0) {
                days = 30; // 使用默认值
            }
            
            // 计算开始时间和结束时间
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusDays(days);
            
            // 调用Mapper获取部门在线时长统计数据
            List<Map<String, Object>> stats = userActivityMapper.getDepartmentOnlineTimeStats(startTime, endTime);
            
            // 如果没有数据，返回空列表
            if (stats == null) {
                return List.of();
            }
            
            // 处理数据，确保数值格式正确
            for (Map<String, Object> stat : stats) {
                // 确保工作日和周末小时数是有效的数值
                Object workdayHours = stat.get("workday_hours");
                Object weekendHours = stat.get("weekend_hours");
                
                // 如果是null，设置为0
                if (workdayHours == null) {
                    stat.put("workday_hours", 0.0);
                }
                
                if (weekendHours == null) {
                    stat.put("weekend_hours", 0.0);
                }
            }
            
            return stats;
        } catch (Exception e) {
            // 记录异常但不抛出，返回空列表
            System.err.println("获取部门在线时长统计数据失败: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
    
    @Override
    public Map<String, Object> getActiveUserStats(Integer days) {
        if (days == null || days <= 0) {
            throw new IllegalArgumentException("天数必须大于0");
        }
        
        // 计算开始时间和结束时间
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);
        
        // 调用已有方法获取统计数据
        return getActiveUserStatsByTimeRange(startTime, endTime);
    }
    
    @Override
    public Map<String, Object> getLastActiveTimeAndOnlineStatus(Integer userId) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 获取最后活跃时间
        LocalDateTime lastActiveTime = getLastActiveTime(userId);
        
        // 获取在线状态
        boolean isOnline = isUserOnline(userId);
        
        // 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("lastActiveTime", lastActiveTime);
        result.put("isOnline", isOnline);
        
        return result;
    }
}