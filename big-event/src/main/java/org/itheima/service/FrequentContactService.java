package org.itheima.service;

import org.itheima.pojo.FrequentContact;
import org.itheima.pojo.User;

import java.util.List;
import java.util.Map;

/**
 * 常用联系人服务接口
 */
public interface FrequentContactService {
    
    /**
     * 根据ID查询常用联系人
     * 
     * @param id 常用联系人ID
     * @return 常用联系人信息
     */
    FrequentContact findById(Integer id);
    
    /**
     * 查询用户的所有常用联系人
     * 
     * @param userId 用户ID
     * @return 常用联系人列表
     */
    List<FrequentContact> findByUserId(Integer userId);
    
    /**
     * 查询用户的常用联系人（包含用户信息）
     * 
     * @param userId 用户ID
     * @return 常用联系人列表（包含用户信息）
     */
    List<User> findContactsWithUserInfoByUserId(Integer userId);
    
    /**
     * 查询用户的收藏联系人
     * 
     * @param userId 用户ID
     * @return 收藏的联系人列表
     */
    List<FrequentContact> findFavoritesByUserId(Integer userId);
    
    /**
     * 查询用户的收藏联系人（包含用户信息）
     * 
     * @param userId 用户ID
     * @return 收藏的联系人列表（包含用户信息）
     */
    List<User> findFavoritesWithUserInfoByUserId(Integer userId);
    
    /**
     * 查询用户的最近联系人
     * 
     * @param userId 用户ID
     * @param limit 数量限制
     * @return 最近联系人列表
     */
    List<FrequentContact> findMostFrequentByUserId(Integer userId, Integer limit);
    
    /**
     * 查询用户的最近联系人（包含用户信息）
     * 
     * @param userId 用户ID
     * @param limit 数量限制
     * @return 最近联系人列表（包含用户信息）
     */
    List<User> findMostFrequentWithUserInfoByUserId(Integer userId, Integer limit);
    
    /**
     * 查询用户与特定联系人的关系
     * 
     * @param userId 用户ID
     * @param contactId 联系人ID
     * @return 常用联系人信息
     */
    FrequentContact findByUserIdAndContactId(Integer userId, Integer contactId);
    
    /**
     * 添加常用联系人
     * 
     * @param frequentContact 常用联系人信息
     * @return 添加的常用联系人
     */
    FrequentContact add(FrequentContact frequentContact);
    
    /**
     * 更新常用联系人
     * 
     * @param frequentContact 常用联系人信息
     * @return 更新后的常用联系人
     */
    FrequentContact update(FrequentContact frequentContact);
    
    /**
     * 删除常用联系人
     * 
     * @param id 常用联系人ID
     * @return 是否删除成功
     */
    boolean deleteById(Integer id);
    
    /**
     * 删除用户与联系人的关系
     * 
     * @param userId 用户ID
     * @param contactId 联系人ID
     * @return 是否删除成功
     */
    boolean deleteByUserIdAndContactId(Integer userId, Integer contactId);
    
    /**
     * 设置联系人为收藏/取消收藏
     * 
     * @param userId 用户ID
     * @param contactId 联系人ID
     * @param isFavorite 是否收藏
     * @return 更新后的常用联系人
     */
    FrequentContact setFavoriteStatus(Integer userId, Integer contactId, Boolean isFavorite);
    
    /**
     * 检查联系人是否是收藏的
     * 
     * @param userId 用户ID
     * @param contactId 联系人ID
     * @return 是否收藏
     */
    boolean isFavoriteContact(Integer userId, Integer contactId);
    
    /**
     * 获取联系人详细信息
     * 
     * @param userId 用户ID
     * @param contactId 联系人ID
     * @return 联系人详细信息
     */
    Map<String, Object> getContactDetail(Integer userId, Integer contactId);
}