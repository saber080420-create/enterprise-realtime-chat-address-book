package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.dto.UserStatusDTO;

import java.util.List;

/**
 * WebSocket相关操作的Mapper接口
 */
@Mapper
public interface WebSocketMapper {
    
    /**
     * 获取所有在线用户状态
     * 函数级注释：
     * - 兼容 MySQL ONLY_FULL_GROUP_BY 模式。
     * - 通过子查询对 user_activity 聚合 MAX(last_active_time)，避免顶层 GROUP BY 选择非聚合列导致的语法错误。
     */
    @Select("SELECT u.id as user_id, u.username, u.nickname, u.user_pic, true as online, ua.last_active_time " +
            "FROM user u " +
            "JOIN (SELECT user_id, MAX(last_active_time) AS last_active_time FROM user_activity WHERE online = true GROUP BY user_id) ua ON u.id = ua.user_id " +
            "ORDER BY ua.last_active_time DESC")
    List<UserStatusDTO> getAllOnlineUsers();
    
    /**
     * 获取指定部门的在线用户状态
     * 函数级注释：
     * - 使用子查询聚合 last_active_time；顶层仅根据部门筛选，无需 GROUP BY。
     */
    @Select("SELECT u.id as user_id, u.username, u.nickname, u.user_pic, true as online, ua.last_active_time " +
            "FROM user u " +
            "JOIN (SELECT user_id, MAX(last_active_time) AS last_active_time FROM user_activity WHERE online = true GROUP BY user_id) ua ON u.id = ua.user_id " +
            "WHERE u.department_id = #{departmentId} " +
            "ORDER BY ua.last_active_time DESC")
    List<UserStatusDTO> getDepartmentOnlineUsers(Integer departmentId);
    
    /**
     * 获取指定群组的在线用户状态
     * 函数级注释：
     * - 使用子查询聚合 last_active_time，并连接 chat_group_member 过滤群成员；
     * - 兼容 ONLY_FULL_GROUP_BY，移除不必要的 GROUP BY。
     */
    @Select("SELECT u.id as user_id, u.username, u.nickname, u.user_pic, true as online, ua.last_active_time " +
            "FROM user u " +
            "JOIN (SELECT user_id, MAX(last_active_time) AS last_active_time FROM user_activity WHERE online = true GROUP BY user_id) ua ON u.id = ua.user_id " +
            "JOIN chat_group_member gm ON u.id = gm.user_id " +
            "WHERE gm.group_id = #{groupId} " +
            "ORDER BY ua.last_active_time DESC")
    List<UserStatusDTO> getGroupOnlineUsers(Integer groupId);
    
    /**
     * 获取用户状态
     * 
     * @param userId 用户ID
     * @return 用户状态
     */
    @Select("SELECT u.id as user_id, u.username, u.nickname, u.user_pic, " +
            "CASE WHEN ua.online IS NULL THEN false ELSE ua.online END as online, " +
            "ua.last_active_time " +
            "FROM user u " +
            "LEFT JOIN user_activity ua ON u.id = ua.user_id AND ua.online = true " +
            "WHERE u.id = #{userId}")
    UserStatusDTO getUserStatus(Integer userId);
    
    /**
     * 获取多个用户的状态
     * 
     * @param userIds 用户ID列表
     * @return 用户状态列表
     */
    @Select({"<script>",
            "SELECT u.id as user_id, u.username, u.nickname, u.user_pic, " +
            "CASE WHEN ua.online IS NULL THEN false ELSE ua.online END as online, " +
            "ua.last_active_time " +
            "FROM user u " +
            "LEFT JOIN user_activity ua ON u.id = ua.user_id AND ua.online = true " +
            "WHERE u.id IN ",
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>",
            "#{userId}",
            "</foreach>",
            "</script>"})
    List<UserStatusDTO> getUserStatusBatch(List<Integer> userIds);
    
    /**
     * 获取用户的联系人状态列表
     * 
     * @param userId 用户ID
     * @return 联系人状态列表
     */
    @Select("SELECT u.id as user_id, u.username, u.nickname, u.user_pic, " +
            "CASE WHEN ua.online IS NULL THEN false ELSE ua.online END as online, " +
            "ua.last_active_time " +
            "FROM user u " +
            "JOIN frequent_contact fc ON u.id = fc.contact_id " +
            "LEFT JOIN user_activity ua ON u.id = ua.user_id AND ua.online = true " +
            "WHERE fc.user_id = #{userId} " +
            "ORDER BY fc.is_favorite DESC, fc.contact_frequency DESC, fc.last_contact_time DESC")
    List<UserStatusDTO> getContactsStatus(Integer userId);
    
    /**
     * 获取部门所有用户的状态
     * 
     * @param departmentId 部门ID
     * @return 部门用户状态列表
     */
    @Select("SELECT u.id as user_id, u.username, u.nickname, u.user_pic, " +
            "CASE WHEN ua.online IS NULL THEN false ELSE ua.online END as online, " +
            "ua.last_active_time " +
            "FROM user u " +
            "LEFT JOIN user_activity ua ON u.id = ua.user_id AND ua.online = true " +
            "WHERE u.department_id = #{departmentId} " +
            "ORDER BY u.username")
    List<UserStatusDTO> getDepartmentUsersStatus(Integer departmentId);
}