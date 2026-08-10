package org.itheima.service;

import org.itheima.pojo.AnnouncementReadStatus;

import java.util.List;

/**
 * 公告阅读状态服务接口
 */
public interface AnnouncementReadStatusService {
    
    /**
     * 根据ID查询阅读状态
     * 
     * @param id 阅读状态ID
     * @return 阅读状态信息
     */
    AnnouncementReadStatus findById(Integer id);
    
    /**
     * 根据公告ID查询阅读状态
     * 
     * @param announcementId 公告ID
     * @return 阅读状态列表
     */
    List<AnnouncementReadStatus> findByAnnouncementId(Integer announcementId);
    
    /**
     * 根据用户ID查询阅读状态
     * 
     * @param userId 用户ID
     * @return 阅读状态列表
     */
    List<AnnouncementReadStatus> findByUserId(Integer userId);
    
    /**
     * 根据公告ID和用户ID查询阅读状态
     * 
     * @param announcementId 公告ID
     * @param userId 用户ID
     * @return 阅读状态信息
     */
    AnnouncementReadStatus findByAnnouncementIdAndUserId(Integer announcementId, Integer userId);
    
    /**
     * 添加阅读状态
     * 
     * @param readStatus 阅读状态信息
     * @return 添加的阅读状态
     */
    AnnouncementReadStatus add(AnnouncementReadStatus readStatus);
    
    /**
     * 更新阅读状态
     * 
     * @param readStatus 阅读状态信息
     * @return 更新后的阅读状态
     */
    AnnouncementReadStatus update(AnnouncementReadStatus readStatus);
    
    /**
     * 删除阅读状态
     * 
     * @param id 阅读状态ID
     * @return 是否删除成功
     */
    boolean deleteById(Integer id);
    
    /**
     * 标记公告为已读
     * 
     * @param announcementId 公告ID
     * @param userId 用户ID
     * @return 阅读状态信息
     */
    AnnouncementReadStatus markAsRead(Integer announcementId, Integer userId);
    
    /**
     * 批量标记公告为已读
     * 
     * @param announcementIds 公告ID列表
     * @param userId 用户ID
     * @return 标记的数量
     */
    int markMultipleAsRead(List<Integer> announcementIds, Integer userId);
    
    /**
     * 批量标记公告为已读
     * 
     * @param announcementIds 公告ID列表
     * @param userId 用户ID
     * @return 是否标记成功
     */
    boolean markAsReadBatch(List<Integer> announcementIds, Integer userId);
    
    /**
     * 获取用户已读公告ID列表
     * 
     * @param userId 用户ID
     * @return 已读公告ID列表
     */
    List<Integer> findReadAnnouncementIdsByUserId(Integer userId);
    
    /**
     * 获取用户未读公告ID列表
     * 
     * @param userId 用户ID
     * @return 未读公告ID列表
     */
    List<Integer> findUnreadAnnouncementIdsByUserId(Integer userId);
    
    /**
     * 统计公告已读人数
     * 
     * @param announcementId 公告ID
     * @return 已读人数
     */
    Integer countReadByAnnouncementId(Integer announcementId);
    
    /**
     * 统计用户未读公告数量
     * 
     * @param userId 用户ID
     * @return 未读公告数量
     */
    Integer countUnreadByUserId(Integer userId);
    
    /**
     * 获取公告已读用户详情列表
     * @param announcementId 公告ID
     * @return 包含用户基本信息与部门名称的列表
     */
    List<java.util.Map<String, Object>> findReadUsersByAnnouncementId(Integer announcementId);
}