package org.itheima.service;

import org.itheima.pojo.ChatGroup;

import java.util.List;

/**
 * 聊天群组服务接口
 */
public interface ChatGroupService {
    
    /**
     * 根据ID查询群组
     * 
     * @param id 群组ID
     * @return 群组信息
     */
    ChatGroup findById(Integer id);
    
    /**
     * 查询所有群组
     * 
     * @return 群组列表
     */
    List<ChatGroup> findAll();
    
    /**
     * 根据创建者ID查询群组
     * 
     * @param creatorId 创建者ID
     * @return 群组列表
     */
    List<ChatGroup> findByCreatorId(Integer creatorId);
    
    /**
     * 根据用户ID查询用户所在的群组
     * 
     * @param userId 用户ID
     * @return 群组列表
     */
    List<ChatGroup> findByUserId(Integer userId);
    
    /**
     * 创建群组
     * 
     * @param chatGroup 群组信息
     * @param memberIds 初始成员ID列表
     * @return 创建的群组
     */
    ChatGroup createGroup(ChatGroup chatGroup, List<Integer> memberIds);
    
    /**
     * 更新群组
     * 
     * @param chatGroup 群组信息
     * @return 更新后的群组
     */
    ChatGroup updateGroup(ChatGroup chatGroup);
    
    /**
     * 更新群组（带用户ID验证）
     * 
     * @param chatGroup 群组信息
     * @param userId 用户ID
     * @return 更新后的群组
     */
    ChatGroup update(ChatGroup chatGroup, Integer userId);
    
    /**
     * 删除群组
     * 
     * @param id 群组ID
     * @return 是否删除成功
     */
    boolean deleteGroup(Integer id);
    
    /**
     * 删除群组（带用户ID验证）
     * 
     * @param id 群组ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteById(Integer id, Integer userId);
    
    /**
     * 根据群组名称查询群组
     * 
     * @param groupName 群组名称
     * @return 群组列表
     */
    List<ChatGroup> findByName(String groupName);
    
    /**
     * 统计群组成员数量
     * 
     * @param groupId 群组ID
     * @return 成员数量
     */
    Integer countGroupMembers(Integer groupId);
    
    /**
     * 查询所有群组及其成员数量
     * 
     * @return 群组列表及成员数量
     */
    List<ChatGroup> findAllWithMemberCount();
    
    /**
     * 搜索群组
     * 
     * @param keyword 关键词
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 群组列表
     */
    List<ChatGroup> searchGroups(String keyword, Integer limit, Integer offset);
}