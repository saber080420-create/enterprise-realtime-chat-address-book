package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.FrequentContact;
import org.itheima.pojo.User;
import org.itheima.pojo.dto.FrequentContactDTO;

import java.util.List;

/**
 * 常用联系人Mapper接口
 */
@Mapper
public interface FrequentContactMapper {
    
    /**
     * 根据ID查询常用联系人
     * 
     * @param id 联系人ID
     * @return 联系人信息
     */
    @Select("select * from frequent_contact where id = #{id}")
    FrequentContact findById(Integer id);
    
    /**
     * 查询用户的所有常用联系人
     * 
     * @param userId 用户ID
     * @return 联系人列表
     */
    @Select("select * from frequent_contact where user_id = #{userId} order by create_time desc")
    List<FrequentContact> findByUserId(Integer userId);
    
    /**
     * 查询用户的收藏联系人
     * 
     * @param userId 用户ID
     * @return 联系人列表
     */
    @Select("select * from frequent_contact where user_id = #{userId} and is_favorite = true order by create_time desc")
    List<FrequentContact> findFavoritesByUserId(Integer userId);
    
    /**
     * 查询特定用户与特定联系人的关系
     * 
     * @param userId 用户ID
     * @param contactId 联系人ID
     * @return 联系人关系
     */
    @Select("select * from frequent_contact where user_id = #{userId} and contact_id = #{contactId}")
    FrequentContact findByUserIdAndContactId(Integer userId, Integer contactId);
    
    /**
     * 添加常用联系人
     * 
     * @param frequentContact 联系人信息
     */
    @Insert("insert into frequent_contact(user_id, contact_id, is_favorite, create_time, update_time) " +
            "values(#{userId}, #{contactId}, #{isFavorite}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(FrequentContact frequentContact);
    
    /**
     * 更新常用联系人
     * 
     * @param frequentContact 联系人信息
     */
    @Update("update frequent_contact set is_favorite = #{isFavorite}, update_time = now() where id = #{id}")
    void update(FrequentContact frequentContact);
    
    /**
     * 删除常用联系人
     * 
     * @param id 联系人ID
     * @return 影响的行数
     */
    @Delete("delete from frequent_contact where id = #{id}")
    int deleteById(Integer id);
    
    /**
     * 删除用户的所有常用联系人
     * 
     * @param userId 用户ID
     */
    @Delete("delete from frequent_contact where user_id = #{userId}")
    void deleteByUserId(Integer userId);
    
    /**
     * 删除特定用户与特定联系人的关系
     * 
     * @param userId 用户ID
     * @param contactId 联系人ID
     * @return 影响的行数
     */
    @Delete("delete from frequent_contact where user_id = #{userId} and contact_id = #{contactId}")
    int deleteByUserIdAndContactId(Integer userId, Integer contactId);

    /**
     * 删除其他用户对该用户的常用联系人关系（作为contact）
     *
     * @param contactId 被删除用户的ID
     */
    @Delete("delete from frequent_contact where contact_id = #{contactId}")
    void deleteByContactId(Integer contactId);
    
    /**
     * 更新收藏状态
     * 
     * @param userId 用户ID
     * @param contactId 联系人ID
     * @param isFavorite 是否收藏
     */
    @Update("update frequent_contact set is_favorite = #{isFavorite}, update_time = now() " +
            "where user_id = #{userId} and contact_id = #{contactId}")
    void updateFavoriteStatus(Integer userId, Integer contactId, Boolean isFavorite);
    
    /**
     * 查询用户的常用联系人（包含联系人详细信息）
     * 
     * @param userId 用户ID
     * @return 联系人列表（包含详细信息）
     */
    @Select("SELECT fc.*, u.username as contact_name, u.user_pic as contact_avatar, " +
            "d.department_name as department_name, u.position, ua.online, " +
            "(SELECT COUNT(*) FROM chat_message cm " +
            "WHERE ((cm.sender_id = #{userId} AND cm.receiver_id = fc.contact_id) " +
            "OR (cm.sender_id = fc.contact_id AND cm.receiver_id = #{userId})) " +
            "AND cm.is_read = false AND cm.sender_id = fc.contact_id) AS unread_count, " +
            "(SELECT content FROM chat_message " +
            "WHERE (sender_id = #{userId} AND receiver_id = fc.contact_id) " +
            "OR (sender_id = fc.contact_id AND receiver_id = #{userId}) " +
            "ORDER BY send_time DESC LIMIT 1) AS last_message " +
            "FROM frequent_contact fc " +
            "JOIN user u ON fc.contact_id = u.id " +
            "LEFT JOIN department d ON u.department_id = d.id " +
            "LEFT JOIN (SELECT user_id, online FROM user_activity WHERE online = true GROUP BY user_id) ua " +
            "ON fc.contact_id = ua.user_id " +
            "WHERE fc.user_id = #{userId} " +
            "ORDER BY fc.is_favorite DESC, fc.create_time DESC")
    List<FrequentContactDTO> findContactsWithDetailsByUserId(Integer userId);
    
    /**
     * 查询用户的常用联系人（包含用户信息）
     * 
     * @param userId 用户ID
     * @return 联系人列表（包含用户信息）
     */
    @Select("SELECT u.* FROM frequent_contact fc " +
            "JOIN user u ON fc.contact_id = u.id " +
            "WHERE fc.user_id = #{userId} " +
            "ORDER BY fc.is_favorite DESC, fc.create_time DESC")
    List<User> findContactsWithUserInfoByUserId(Integer userId);
    
    /**
     * 查询用户的最近联系人（按创建时间排序）
     * 
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 最近联系人列表
     */
    @Select("SELECT * FROM frequent_contact " +
            "WHERE user_id = #{userId} " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit}")
    List<FrequentContact> findMostFrequentByUserId(Integer userId, Integer limit);
    
    /**
     * 查询用户的最近联系人（包含用户信息，按创建时间排序）
     * 
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 最近联系人列表（包含用户信息）
     */
    @Select("SELECT u.* FROM frequent_contact fc " +
            "JOIN user u ON fc.contact_id = u.id " +
            "WHERE fc.user_id = #{userId} " +
            "ORDER BY fc.create_time DESC " +
            "LIMIT #{limit}")
    List<User> findMostFrequentWithUserInfoByUserId(Integer userId, Integer limit);
    
    /**
     * 查询用户的收藏联系人（包含联系人详细信息）
     * 
     * @param userId 用户ID
     * @return 联系人列表（包含详细信息）
     */
    @Select("SELECT fc.*, u.username as contact_name, u.user_pic as contact_avatar, " +
            "d.department_name as department_name, u.position, ua.online, " +
            "(SELECT COUNT(*) FROM chat_message cm " +
            "WHERE ((cm.sender_id = #{userId} AND cm.receiver_id = fc.contact_id) " +
            "OR (cm.sender_id = fc.contact_id AND cm.receiver_id = #{userId})) " +
            "AND cm.is_read = false AND cm.sender_id = fc.contact_id) AS unread_count, " +
            "(SELECT content FROM chat_message " +
            "WHERE (sender_id = #{userId} AND receiver_id = fc.contact_id) " +
            "OR (sender_id = fc.contact_id AND receiver_id = #{userId}) " +
            "ORDER BY send_time DESC LIMIT 1) AS last_message " +
            "FROM frequent_contact fc " +
            "JOIN user u ON fc.contact_id = u.id " +
            "LEFT JOIN department d ON u.department_id = d.id " +
            "LEFT JOIN (SELECT user_id, online FROM user_activity WHERE online = true GROUP BY user_id) ua " +
            "ON fc.contact_id = ua.user_id " +
            "WHERE fc.user_id = #{userId} AND fc.is_favorite = true " +
            "ORDER BY fc.create_time DESC")
    List<FrequentContactDTO> findFavoriteContactsWithDetailsByUserId(Integer userId);
    
    /**
     * 查询用户的收藏联系人（包含用户信息）
     * 
     * @param userId 用户ID
     * @return 收藏联系人列表（包含用户信息）
     */
    @Select("SELECT u.* FROM frequent_contact fc " +
            "JOIN user u ON fc.contact_id = u.id " +
            "WHERE fc.user_id = #{userId} AND fc.is_favorite = true " +
            "ORDER BY fc.create_time DESC")
    List<User> findFavoritesWithUserInfoByUserId(Integer userId);
}