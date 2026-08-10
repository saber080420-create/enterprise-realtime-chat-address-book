package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.ChatGroupMember;
import org.itheima.pojo.User;

import java.util.List;

/**
 * 聊天群组成员Mapper接口
 */
@Mapper
public interface ChatGroupMemberMapper {
    
    /**
     * 根据ID查询群组成员
     * 
     * @param id 成员ID
     * @return 成员信息
     */
    @Select("select * from chat_group_member where id = #{id}")
    ChatGroupMember findById(Integer id);
    
    /**
     * 查询群组的所有成员
     * 
     * @param groupId 群组ID
     * @return 成员列表
     */
    @Select("select * from chat_group_member where group_id = #{groupId} order by role asc, join_time asc")
    List<ChatGroupMember> findByGroupId(Integer groupId);
    
    /**
     * 查询用户加入的所有群组成员关系
     * 
     * @param userId 用户ID
     * @return 成员列表
     */
    @Select("select * from chat_group_member where user_id = #{userId}")
    List<ChatGroupMember> findByUserId(Integer userId);
    
    /**
     * 查询特定用户在特定群组中的成员信息
     * 
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 成员信息
     */
    @Select("select * from chat_group_member where group_id = #{groupId} and user_id = #{userId}")
    ChatGroupMember findByGroupIdAndUserId(Integer groupId, Integer userId);
    
    /**
     * 添加群组成员
     * 
     * @param chatGroupMember 成员信息
     */
    @Insert("insert into chat_group_member(group_id, user_id, role, alias, mute, join_time, create_time, update_time) " +
            "values(#{groupId}, #{userId}, #{role}, #{alias}, #{mute}, now(), now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(ChatGroupMember chatGroupMember);
    
    /**
     * 更新群组成员
     * 
     * @param chatGroupMember 成员信息
     */
    @Update("update chat_group_member set role = #{role}, alias = #{alias}, mute = #{mute}, " +
            "update_time = now() where id = #{id}")
    void update(ChatGroupMember chatGroupMember);
    
    /**
     * 删除群组成员
     * 
     * @param id 成员ID
     */
    @Delete("delete from chat_group_member where id = #{id}")
    void deleteById(Integer id);
    
    /**
     * 删除群组的所有成员
     * 
     * @param groupId 群组ID
     */
    @Delete("delete from chat_group_member where group_id = #{groupId}")
    void deleteByGroupId(Integer groupId);
    
    /**
     * 删除用户加入的所有群组成员关系
     * 
     * @param userId 用户ID
     */
    @Delete("delete from chat_group_member where user_id = #{userId}")
    void deleteByUserId(Integer userId);
    
    /**
     * 删除特定用户在特定群组中的成员关系
     * 
     * @param groupId 群组ID
     * @param userId 用户ID
     */
    @Delete("delete from chat_group_member where group_id = #{groupId} and user_id = #{userId}")
    void deleteByGroupIdAndUserId(Integer groupId, Integer userId);
    
    /**
     * 查询群组管理员
     * 
     * @param groupId 群组ID
     * @return 管理员列表
     */
    @Select("select * from chat_group_member where group_id = #{groupId} and role in ('owner', 'admin')")
    List<ChatGroupMember> findAdminsByGroupId(Integer groupId);
    
    // 已移除：查询群组管理员的用户信息（未接入前端）
    
    /**
     * 查询群主
     * 
     * @param groupId 群组ID
     * @return 群主信息
     */
    @Select("select * from chat_group_member where group_id = #{groupId} and role = 'owner'")
    ChatGroupMember findOwnerByGroupId(Integer groupId);
    
    /**
     * 更新成员角色
     * 
     * @param id 成员ID
     * @param role 角色
     */
    // 已移除：更新成员角色
    
    /**
     * 更新成员禁言状态
     * 
     * @param id 成员ID
     * @param mute 是否禁言
     */
    // 已移除：更新成员禁言
    
    /**
     * 更新成员群内昵称
     * 
     * @param id 成员ID
     * @param alias 群内昵称
     */
    // 已移除：更新成员群内昵称
    
    /**
     * 批量添加群组成员
     * 
     * @param groupId 群组ID
     * @param userIds 用户ID列表
     * @param role 角色
     * @return 添加的成员数量
     */
    @Insert({"<script>",
            "insert into chat_group_member(group_id, user_id, role, mute, join_time, create_time, update_time) values ",
            "<foreach collection='userIds' item='userId' separator=','>",
            "(#{groupId}, #{userId}, #{role}, false, now(), now(), now())",
            "</foreach>",
            "</script>"})
    int batchAddMembers(Integer groupId, List<Integer> userIds, String role);
    
    /**
     * 根据群组ID查询所有成员的用户信息
     * 
     * @param groupId 群组ID
     * @return 用户列表
     */
    @Select("SELECT u.* FROM user u JOIN chat_group_member cgm ON u.id = cgm.user_id WHERE cgm.group_id = #{groupId} ORDER BY cgm.role ASC, cgm.join_time ASC")
    List<User> findUsersByGroupId(Integer groupId);
    
    /**
     * 批量添加群组成员
     * 
     * @param members 成员列表
     * @return 添加的成员数量
     */
    @Insert({"<script>",
            "insert into chat_group_member(group_id, user_id, role, alias, mute, join_time, create_time, update_time) values ",
            "<foreach collection='list' item='member' separator=','>",
            "(#{member.groupId}, #{member.userId}, #{member.role}, #{member.alias}, #{member.mute}, #{member.joinTime}, #{member.createTime}, #{member.updateTime})",
            "</foreach>",
            "</script>"})
    int batchAdd(List<ChatGroupMember> members);
    
    /**
     * 统计群组成员数量
     * 
     * @param groupId 群组ID
     * @return 成员数量
     */
    @Select("select count(*) from chat_group_member where group_id = #{groupId}")
    Integer countByGroupId(Integer groupId);
}