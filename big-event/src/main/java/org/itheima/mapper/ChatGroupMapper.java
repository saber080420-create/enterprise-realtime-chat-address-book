package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.ChatGroup;

import java.util.List;

/**
 * 聊天群组Mapper接口
 */
@Mapper
public interface ChatGroupMapper {
    
    /**
     * 根据ID查询群组
     * 
     * @param id 群组ID
     * @return 群组信息
     */
    @Select("select * from chat_group where id = #{id}")
    ChatGroup findById(Integer id);
    
    /**
     * 查询所有群组
     * 
     * @return 群组列表
     */
    @Select("select * from chat_group order by create_time desc")
    List<ChatGroup> findAll();
    
    /**
     * 查询用户创建的群组
     * 
     * @param creatorId 创建者ID
     * @return 群组列表
     */
    @Select("select * from chat_group where creator_id = #{creatorId} order by create_time desc")
    List<ChatGroup> findByCreatorId(Integer creatorId);
    
    /**
     * 查询用户加入的群组
     * 
     * @param userId 用户ID
     * @return 群组列表
     */
    @Select("select g.* from chat_group g " +
            "inner join chat_group_member m on g.id = m.group_id " +
            "where m.user_id = #{userId} " +
            "order by g.create_time desc")
    List<ChatGroup> findByUserId(Integer userId);
    
    /**
     * 添加群组
     * 
     * @param chatGroup 群组信息
     */
    @Insert("insert into chat_group(group_name, group_avatar, description, creator_id, create_time, update_time) " +
            "values(#{groupName}, #{groupAvatar}, #{description}, #{creatorId}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(ChatGroup chatGroup);
    
    /**
     * 更新群组
     * 
     * @param chatGroup 群组信息
     */
    @Update("update chat_group set group_name = #{groupName}, group_avatar = #{groupAvatar}, " +
            "description = #{description}, update_time = now() where id = #{id}")
    void update(ChatGroup chatGroup);
    
    /**
     * 删除群组
     * 
     * @param id 群组ID
     */
    @Delete("delete from chat_group where id = #{id}")
    void deleteById(Integer id);
    
    /**
     * 根据群组名称查询群组
     * 
     * @param groupName 群组名称
     * @return 群组列表
     */
    @Select("select * from chat_group where group_name like concat('%', #{groupName}, '%') order by create_time desc")
    List<ChatGroup> findByName(String groupName);
    
    /**
     * 统计群组成员数量
     * 
     * @param groupId 群组ID
     * @return 成员数量
     */
    @Select("select count(*) from chat_group_member where group_id = #{groupId}")
    Integer countMembersByGroupId(Integer groupId);
    
    /**
     * 查询所有群组及其成员数量
     * 
     * @return 群组列表及成员数量
     */
    @Select("SELECT g.*, (SELECT COUNT(*) FROM chat_group_member m WHERE m.group_id = g.id) AS memberCount " +
            "FROM chat_group g ORDER BY g.create_time DESC")
    List<ChatGroup> findAllWithMemberCount();
    
    /**
     * 搜索群组
     * 
     * @param keyword 关键字
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 群组列表
     */
    @Select("select * from chat_group where group_name like concat('%', #{keyword}, '%') " +
            "or description like concat('%', #{keyword}, '%') " +
            "order by create_time desc limit #{limit} offset #{offset}")
    List<ChatGroup> searchGroups(String keyword, Integer limit, Integer offset);
}