package org.itheima.service;

import org.itheima.pojo.Announcement;

import java.util.List;
import java.util.Map;

/**
 * 公告服务接口
 */
public interface AnnouncementService {
    
    /**
     * 根据ID查询公告
     * 
     * @param id 公告ID
     * @return 公告信息
     */
    Announcement findById(Integer id);
    
    /**
     * 查询所有公告
     * 
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    List<Announcement> findAll(Integer limit, Integer offset);
    
    /**
     * 根据部门ID查询公告
     * 
     * @param departmentId 部门ID
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    List<Announcement> findByDepartmentId(Integer departmentId, Integer limit, Integer offset);
    
    /**
     * 根据类型查询公告
     * 
     * @param type 公告类型
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    List<Announcement> findByType(String type, Integer limit, Integer offset);
    
    /**
     * 根据发布者ID查询公告
     * 
     * @param publisherId 发布者ID
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    List<Announcement> findByPublisherId(Integer publisherId, Integer limit, Integer offset);
    
    /**
     * 添加公告
     * 
     * @param announcement 公告信息
     * @return 添加的公告
     */
    Announcement add(Announcement announcement);
    
    /**
     * 更新公告
     * 
     * @param announcement 公告信息
     * @param userId 用户ID
     * @return 更新后的公告
     */
    Announcement update(Announcement announcement, Integer userId);
    
    /**
     * 删除公告
     * 
     * @param id 公告ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteById(Integer id, Integer userId);
    
    /**
     * 更新公告状态
     * 
     * @param id 公告ID
     * @param status 状态
     * @return 更新后的公告
     */
    Announcement updateStatus(Integer id, String status);
    
    /**
     * 分页查询所有公告
     * 
     * @param page 页码
     * @param pageSize 每页数量
     * @return 公告列表和总数
     */
    Map<String, Object> findAllByPage(Integer page, Integer pageSize);
    
    /**
     * 分页查询公告（支持按类型和部门ID筛选）
     * 
     * @param page 页码
     * @param pageSize 每页数量
     * @param type 公告类型（company或department）
     * @param departmentId 部门ID（当type为department时使用）
     * @return 公告列表和总数
     */
    Map<String, Object> findByPage(Integer page, Integer pageSize, String type, Integer departmentId);
    
    /**
     * 查询用户可见的公告
     * 
     * @param userId 用户ID
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    List<Announcement> findVisibleToUser(Integer userId, Integer limit, Integer offset);
    
    /**
     * 查询公告及其阅读状态
     * 
     * @param userId 用户ID
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表及阅读状态
     */
    List<Map<String, Object>> findWithReadStatus(Integer userId, Integer limit, Integer offset);
    
    /**
     * 统计公告阅读情况
     * 
     * @param announcementId 公告ID
     * @return 阅读统计
     */
    Map<String, Object> getReadStats(Integer announcementId);
    
    /**
     * 搜索公告
     * 
     * @param keyword 关键词
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    List<Announcement> searchAnnouncements(String keyword, Integer limit, Integer offset);
    
    // 此方法已被findAllByPage替代，为避免重复声明而移除
    
    /**
     * 根据创建者ID查询公告
     * 
     * @param creatorId 创建者ID
     * @return 公告列表
     */
    List<Announcement> findByCreatorId(Integer creatorId);
    
    /**
     * 查询重要公告
     * 
     * @return 重要公告列表
     */
    List<Announcement> findImportant();
    
    /**
     * 查询最新公告
     * 
     * @param limit 限制数量
     * @return 最新公告列表
     */
    List<Announcement> findLatest(Integer limit);
    
    /**
     * 根据标题搜索公告
     * 
     * @param title 标题关键词
     * @return 公告列表
     */
    List<Announcement> findByTitle(String title);
}