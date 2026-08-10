package org.itheima.service;

import org.itheima.pojo.ChatGroupMember;
import org.itheima.pojo.User;

import java.util.List;

/**
 * 聊天群组成员服务接口
 */
public interface ChatGroupMemberService {
    
    /**
     * 根据ID查询群组成员
     * 
     * @param id 成员ID
     * @return 成员信息
     */
    ChatGroupMember findById(Integer id);
    
    /**
     * 根据群组ID查询所有成员
     * 
     * @param groupId 群组ID
     * @return 成员列表
     */
    List<ChatGroupMember> findByGroupId(Integer groupId);
    
    /**
     * 根据群组ID查询所有成员（包含用户信息）
     * 
     * @param groupId 群组ID
     * @return 成员列表（包含用户信息）
     */
    List<User> findMembersByGroupId(Integer groupId);
    
    /**
     * 根据群组ID和用户ID查询成员
     * 
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 成员信息
     */
    ChatGroupMember findByGroupIdAndUserId(Integer groupId, Integer userId);
    
    /**
     * 添加群组成员
     * 
     * @param chatGroupMember 成员信息
     * @return 添加的成员
     */
    ChatGroupMember addMember(ChatGroupMember chatGroupMember);
    
    /**
     * 批量添加群组成员
     * 
     * @param groupId 群组ID
     * @param userIds 用户ID列表
     * @param operatorId 操作者ID
     * @return 添加的成员数量
     */
    int addMembers(Integer groupId, List<Integer> userIds, Integer operatorId);
    
    /**
     * 更新群组成员
     * 
     * @param chatGroupMember 成员信息
     * @param operatorId 操作者ID
     * @return 更新后的成员
     */
    ChatGroupMember updateMember(ChatGroupMember chatGroupMember, Integer operatorId);
    
    /**
     * 删除群组成员
     * 
     * @param id 成员ID
     * @param operatorId 操作者ID
     * @return 是否删除成功
     */
    boolean deleteMember(Integer id, Integer operatorId);
    
    /**
     * 移除群组成员
     * 
     * @param groupId 群组ID
     * @param userId 用户ID
     * @param operatorId 操作者ID
     * @return 是否移除成功
     */
    boolean removeMember(Integer groupId, Integer userId, Integer operatorId);
    
    // 已移除：管理员相关查询（未接入前端）
    
    /**
     * 查询群主
     * 
     * @param groupId 群组ID
     * @return 群主信息
     */
    ChatGroupMember findOwnerByGroupId(Integer groupId);
    
    // 已移除：更新成员角色（未接入前端）
    
    // 已移除：更新成员禁言状态（未接入前端）
    
    // 已移除：更新成员群内昵称（未接入前端）
    
    /**
     * 检查用户是否是群组成员
     * 
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否是成员
     */
    boolean isMember(Integer groupId, Integer userId);
    
    /**
     * 检查用户是否是群组管理员或群主
     * 
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否是管理员或群主
     */
    boolean isAdminOrOwner(Integer groupId, Integer userId);
    
    /**
     * 检查用户是否是群主
     * 
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否是群主
     */
    boolean isOwner(Integer groupId, Integer userId);
    
    /**
     * 获取用户在群组中的角色
     * 
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 用户角色
     */
    String getMemberRole(Integer groupId, Integer userId);
}