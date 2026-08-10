package org.itheima.service.impl;

import org.itheima.mapper.AnnouncementMapper;
import org.itheima.mapper.AnnouncementReadStatusMapper;
import org.itheima.mapper.DepartmentMapper;
import org.itheima.mapper.UserMapper;
import org.itheima.pojo.Announcement;
import org.itheima.pojo.AnnouncementReadStatus;
import org.itheima.service.AnnouncementService;
import org.itheima.service.DepartmentService;
import org.itheima.service.WebSocketService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 公告服务实现类
 */
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    @Autowired
    private AnnouncementMapper announcementMapper;
    
    @Autowired
    private AnnouncementReadStatusMapper readStatusMapper;
    
    @Autowired
    private DepartmentService departmentService;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private WebSocketService webSocketService;

    @Override
    public Announcement findById(Integer id) {
        return announcementMapper.findById(id);
    }

    @Override
    public List<Announcement> findAll(Integer limit, Integer offset) {
        return announcementMapper.findAll(limit, offset);
    }

    @Override
    public List<Announcement> findByDepartmentId(Integer departmentId, Integer limit, Integer offset) {
        return announcementMapper.findByDepartmentId(departmentId, limit, offset);
    }

    @Override
    public List<Announcement> findByType(String type, Integer limit, Integer offset) {
        return announcementMapper.findByType(type, limit, offset);
    }

    @Override
    public List<Announcement> findByPublisherId(Integer publisherId, Integer limit, Integer offset) {
        return announcementMapper.findByPublisherId(publisherId, limit, offset);
    }
    
    @Override
    public List<Announcement> findByCreatorId(Integer creatorId) {
        // 在 Announcement 实体中，创建者 ID 实际上是 publisherId
        // 使用默认值 1000 和 0 作为 limit 和 offset，以获取所有公告
        return announcementMapper.findByPublisherId(creatorId, 1000, 0);
    }

    @Override
    @Transactional
    public Announcement add(Announcement announcement) {
        // 不再设置publisherId，由控制器层统一设置
        
        // 设置创建时间
        LocalDateTime now = LocalDateTime.now();
        announcement.setCreateTime(now);
        announcement.setUpdateTime(now);
        
        // 如果状态为已发布，设置发布时间
        if ("published".equals(announcement.getStatus())) {
            announcement.setPublishTime(now);
        }
        
        // 保存公告
        announcementMapper.add(announcement);
        
        // 如果公告状态为已发布，通过WebSocket发送通知
        if ("published".equals(announcement.getStatus())) {
            // 广播公告通知给所有在线用户
            webSocketService.broadcastAnnouncement(announcement);
        }
        
        return announcement;
    }

    @Override
    @Transactional
    /**
     * 更新公告（支持部分字段更新）
     * 逻辑：
     * 1. 校验公告存在与权限（发布者、本部门管理员或系统管理员）
     * 2. 若从草稿变为已发布，自动写入当前发布时间并广播通知
     * 3. 对未在请求体中提供的字段使用数据库原值，避免把非空字段更新为NULL
     * 4. 非发布状态变更时保留原publishTime
     * 5. 始终更新updateTime为当前时间
     *
     * @param announcement 前端传入的公告部分字段（必须包含id，title/content可更新）
     * @param userId 当前操作用户ID
     * @return 更新后的公告
     * @throws RuntimeException 公告不存在或无权限等业务异常
     */
    public Announcement update(Announcement announcement, Integer userId) {
        // 检查公告是否存在
        Announcement existingAnnouncement = announcementMapper.findById(announcement.getId());
        if (existingAnnouncement == null) {
            throw new RuntimeException("公告不存在");
        }
        
        // 获取当前登录用户角色
        Map<String, Object> map = ThreadLocalUtil.get();
        String role = (String) map.get("role");
        
        // 检查是否是发布者或管理员
        if (!existingAnnouncement.getPublisherId().equals(userId) && 
                !"system_admin".equals(role) && !"department_admin".equals(role)) {
            throw new RuntimeException("没有权限修改该公告");
        }
        
        // 检查是否从草稿变为已发布状态
        boolean isPublishingDraft = "draft".equals(existingAnnouncement.getStatus()) && 
                "published".equals(announcement.getStatus());
        
        // 检查是否为已发布公告的编辑（状态仍为published）
        boolean isEditingPublished = "published".equals(existingAnnouncement.getStatus());
        
        // 记录是否需要重置阅读状态与广播更新
        boolean needResetReadStatusAndNotify = false;
        
        // 如果是已发布公告，且标题/内容/类型/部门等关键字段发生变更，则需要重置
        if (isEditingPublished) {
            String newTitle = announcement.getTitle() != null ? announcement.getTitle() : existingAnnouncement.getTitle();
            String newContent = announcement.getContent() != null ? announcement.getContent() : existingAnnouncement.getContent();
            String newType = announcement.getType() != null ? announcement.getType() : existingAnnouncement.getType();
            Integer newDeptId = announcement.getDepartmentId() != null ? announcement.getDepartmentId() : existingAnnouncement.getDepartmentId();
            
            if (!newTitle.equals(existingAnnouncement.getTitle()) ||
                !newContent.equals(existingAnnouncement.getContent()) ||
                (newType != null && !newType.equals(existingAnnouncement.getType())) ||
                (newDeptId != null && (existingAnnouncement.getDepartmentId() == null || !newDeptId.equals(existingAnnouncement.getDepartmentId())))) {
                needResetReadStatusAndNotify = true;
            }
        }
        
        // 如果状态从草稿变为已发布，设置发布时间
        if (isPublishingDraft) {
            announcement.setPublishTime(LocalDateTime.now());
        }
        
        // 补全未提供的字段，避免将非空字段更新为NULL
        if (announcement.getPublisherId() == null) {
            announcement.setPublisherId(existingAnnouncement.getPublisherId());
        }
        if (announcement.getType() == null) {
            announcement.setType(existingAnnouncement.getType());
        }
        if (announcement.getDepartmentId() == null) {
            announcement.setDepartmentId(existingAnnouncement.getDepartmentId());
        }
        if (announcement.getStatus() == null) {
            announcement.setStatus(existingAnnouncement.getStatus());
        }
        if (announcement.getTitle() == null) {
            announcement.setTitle(existingAnnouncement.getTitle());
        }
        if (announcement.getContent() == null) {
            announcement.setContent(existingAnnouncement.getContent());
        }
        // 如果不是从草稿发布，保留原发布时间
        if (!isPublishingDraft) {
            announcement.setPublishTime(existingAnnouncement.getPublishTime());
        }
        
        // 更新公告信息
        announcement.setUpdateTime(LocalDateTime.now());
        announcementMapper.update(announcement);
        
        // 如果公告状态从草稿变为已发布，通过WebSocket发送通知
        if (isPublishingDraft) {
            webSocketService.broadcastAnnouncement(announcement);
        }
        
        // 如果是对已发布公告的内容修改，则重置阅读状态并广播“公告更新”
        if (isEditingPublished && needResetReadStatusAndNotify) {
            // 1) 重置读取状态为未读
            readStatusMapper.resetReadStatusesByAnnouncementId(announcement.getId());
            // 2) 通过 WebSocket 广播公告（前端可识别为更新）
            webSocketService.broadcastAnnouncement(announcement);
        }
        
        return announcement;
    }

    @Override
    @Transactional
    public boolean deleteById(Integer id, Integer userId) {
        // 检查公告是否存在
        Announcement existingAnnouncement = announcementMapper.findById(id);
        if (existingAnnouncement == null) {
            throw new RuntimeException("公告不存在");
        }
        
        // 获取当前登录用户角色
        Map<String, Object> map = ThreadLocalUtil.get();
        String role = (String) map.get("role");
        
        // 检查是否是发布者或管理员
        if (!existingAnnouncement.getPublisherId().equals(userId) && 
                !"system_admin".equals(role) && !"department_admin".equals(role)) {
            throw new RuntimeException("没有权限删除该公告");
        }
        
        // 删除公告阅读状态
        readStatusMapper.deleteByAnnouncementId(id);
        
        // 删除公告
        announcementMapper.deleteById(id);
        
        return true;
    }

    @Override
    @Transactional
    public Announcement updateStatus(Integer id, String status) {
        // 检查公告是否存在
        Announcement existingAnnouncement = announcementMapper.findById(id);
        if (existingAnnouncement == null) {
            throw new RuntimeException("公告不存在");
        }
        
        // 获取当前登录用户ID
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        // 检查是否是发布者或管理员
        String role = (String) map.get("role");
        if (!existingAnnouncement.getPublisherId().equals(userId) && 
                !"system_admin".equals(role) && !"department_admin".equals(role)) {
            throw new RuntimeException("没有权限修改该公告状态");
        }
        
        // 检查是否从草稿变为已发布状态
        boolean isPublishingDraft = "draft".equals(existingAnnouncement.getStatus()) && "published".equals(status);
        // 检查是否撤回公告
        boolean isRevokingAnnouncement = "published".equals(existingAnnouncement.getStatus()) && "revoked".equals(status);
        
        // 如果状态从草稿变为已发布，设置发布时间
        LocalDateTime now = LocalDateTime.now();
        if (isPublishingDraft) {
            existingAnnouncement.setPublishTime(now);
        }
        
        // 更新状态
        existingAnnouncement.setStatus(status);
        existingAnnouncement.setUpdateTime(now);
        announcementMapper.update(existingAnnouncement);
        
        // 如果公告状态从草稿变为已发布，通过WebSocket发送通知
        if (isPublishingDraft) {
            // 广播公告通知给所有在线用户
            webSocketService.broadcastAnnouncement(existingAnnouncement);
        }
        
        // 如果公告被撤回，通过WebSocket发送撤回通知
        if (isRevokingAnnouncement) {
            // 广播公告撤回通知给所有在线用户
            webSocketService.broadcastAnnouncementRevoke(existingAnnouncement);
        }
        
        return existingAnnouncement;
    }

    @Override
    public List<Announcement> findVisibleToUser(Integer userId, Integer limit, Integer offset) {
        // 获取用户所在部门ID
        Integer departmentId = userMapper.findByUserName(userId.toString()).getDepartmentId();
        
        // 获取用户所在部门及其所有父部门ID
        List<Integer> departmentIds = departmentService.getDepartmentAndChildrenIds(departmentId);
        
        return announcementMapper.findVisibleToUser(userId, departmentIds, limit, offset);
    }

    @Override
    public List<Map<String, Object>> findWithReadStatus(Integer userId, Integer limit, Integer offset) {
        // 调用mapper查询公告及其阅读状态
        // 添加必要的导入
        
        // 使用Map<String,Object>接收公告及其阅读状态信息
        // 调用mapper查询公告及阅读状态,返回Map列表包含公告信息和阅读状态
        List<Map<String, Object>> announcements = announcementMapper.findWithReadStatus(userId, limit, offset);
        if (announcements == null) {
            return new ArrayList<>();
        }
        return announcements;
    }

    @Override
    public Map<String, Object> getReadStats(Integer announcementId) {
        // 检查公告是否存在
        Announcement announcement = announcementMapper.findById(announcementId);
        if (announcement == null) {
            throw new RuntimeException("公告不存在");
        }
        
        // 获取已读人数
        Integer readCount = readStatusMapper.countReadByAnnouncementId(announcementId);
        
        // 获取总人数（根据公告类型确定目标人群）
        Integer totalCount = 0;
        if ("company".equals(announcement.getType())) {
            // 公司公告，目标是所有用户
            // 这里需要调用UserMapper的方法获取总用户数
            // 假设有一个countAll方法
            // totalCount = userMapper.countAll();
            totalCount = 100; // 临时写死，实际应该从数据库查询
        } else if ("department".equals(announcement.getType()) && announcement.getDepartmentId() != null) {
            // 部门公告，目标是部门用户
            totalCount = departmentService.countUsersByDepartmentId(announcement.getDepartmentId());
        }
        
        // 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("announcementId", announcementId);
        result.put("readCount", readCount);
        result.put("totalCount", totalCount);
        result.put("readRate", totalCount > 0 ? (double) readCount / totalCount : 0);
        
        return result;
    }

    @Override
    public List<Announcement> searchAnnouncements(String keyword, Integer limit, Integer offset) {
        return announcementMapper.searchAnnouncements(keyword, limit, offset);
    }
    
    /**
     * 分页查询所有公告
     * 
     * @param page 页码
     * @param pageSize 每页数量
     * @return 包含公告列表和分页信息的Map
     */
    @Override
    public Map<String, Object> findAllByPage(Integer page, Integer pageSize) {
        // 计算偏移量
        Integer offset = (page - 1) * pageSize;
        
        // 查询公告列表
        List<Announcement> announcements = announcementMapper.findAll(pageSize, offset);
        
        // 查询总记录数
        Integer total = announcementMapper.countAll();
        
        // 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", announcements);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        
        return result;
    }
    
    @Override
    public List<Announcement> findImportant() {
        return announcementMapper.findImportant();
    }
    
    @Override
    public List<Announcement> findLatest(Integer limit) {
        return announcementMapper.findLatest(limit);
    }
    
    @Override
    public List<Announcement> findByTitle(String title) {
        return announcementMapper.findByTitle(title);
    }
    
    /**
     * 分页查询公告（支持按类型和部门ID筛选）
     * 
     * @param page 页码
     * @param pageSize 每页数量
     * @param type 公告类型（company或department）
     * @param departmentId 部门ID（当type为department时使用）
     * @return 包含公告列表和分页信息的Map
     */
    @Override
    public Map<String, Object> findByPage(Integer page, Integer pageSize, String type, Integer departmentId) {
        // 计算偏移量
        Integer offset = (page - 1) * pageSize;
        
        // 查询公告列表（获取Map格式数据，包含联表查询的字段）
        List<Map<String, Object>> announcements = announcementMapper.findByScopeWithDetails(type, departmentId, pageSize, offset);
        
        // 查询总记录数
        Integer total = announcementMapper.countByScope(type, departmentId);
        
        // 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", announcements);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        
        return result;
    }
}