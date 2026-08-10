package org.itheima.controller;

import jakarta.validation.Valid;
import org.itheima.pojo.Announcement;
import org.itheima.pojo.Result;
import org.itheima.service.AnnouncementService;
import org.itheima.service.AnnouncementReadStatusService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

/**
 * 公告控制器
 */
@RestController
@RequestMapping("/announcement")
@Validated
public class AnnouncementController {

    @Autowired
    private AnnouncementService announcementService;
    
    @Autowired
    private AnnouncementReadStatusService announcementReadStatusService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 创建公告
     * 
     * @param announcement 公告信息
     * @return 创建结果
     */
    @PostMapping
    public Result<Announcement> createAnnouncement(@RequestBody @Valid Announcement announcement) {
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer publisherId = (Integer) claims.get("id");
            String userRole = (String) claims.get("role");
            Integer departmentId = (Integer) claims.get("departmentId");
            
            // 检查权限：系统管理员可以发布全公司公告，部门管理员只能发布本部门公告
            if (announcement.getType() == null || "company".equals(announcement.getType())) {
                if (!"system_admin".equals(userRole)) {
                    return Result.error("权限不足，只有系统管理员可以发布全公司公告");
                }
            } else if ("department".equals(announcement.getType())) {
                if (!"system_admin".equals(userRole) && !"department_admin".equals(userRole)) {
                    return Result.error("权限不足，只有系统管理员和部门管理员可以发布部门公告");
                }
                
                // 部门管理员只能发布自己所在部门的公告
                if ("department_admin".equals(userRole) && 
                    (announcement.getDepartmentId() == null || !announcement.getDepartmentId().equals(departmentId))) {
                    announcement.setDepartmentId(departmentId); // 强制设置为自己所在部门
                }
            }
            
            // 设置发布者ID为当前登录用户
            announcement.setPublisherId(publisherId);
            
            Announcement createdAnnouncement = announcementService.add(announcement);
            return Result.success(createdAnnouncement);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有公告
     * 
     * @param page 页码
     * @param pageSize 每页数量
     * @param type 公告类型（可选，company或department）
     * @return 公告列表
     */
    @GetMapping
    public Result<Map<String, Object>> getAllAnnouncements(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type) {
        
        // 获取当前登录用户信息
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        String userRole = (String) claims.get("role");
        Integer departmentId = (Integer) claims.get("departmentId");
        
        // 根据用户角色和请求参数确定查询范围
        Map<String, Object> result;
        if ("system_admin".equals(userRole)) {
            // 系统管理员可以查看所有公告
            result = announcementService.findByPage(page, pageSize, type, null);
        } else if ("department_admin".equals(userRole)) {
            // 部门管理员可以查看全公司公告和自己部门的公告
            result = announcementService.findByPage(page, pageSize, type, departmentId);
        } else {
            // 普通员工只能查看全公司公告和自己部门的公告
            result = announcementService.findByPage(page, pageSize, type, departmentId);
        }
        
        // 为每条公告补充当前用户的已读状态 isRead，并根据“我已删除的公告会话”过滤
        if (result != null && result.get("records") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
            // 查询当前用户的已读公告ID列表，避免 N+1 查询
            List<Integer> readIds = announcementReadStatusService.findReadAnnouncementIdsByUserId(userId);
            java.util.Set<Integer> readIdSet = new java.util.HashSet<>(readIds);
            java.util.Set<String> deletedIdSet = new java.util.HashSet<>();
            try {
                if (stringRedisTemplate != null) {
                    String key = "user:ann:deleted:" + userId;
                    java.util.Set<String> members = stringRedisTemplate.opsForSet().members(key);
                    if (members != null) deletedIdSet.addAll(members);
                }
            } catch (Exception ignored) {}

            java.util.Iterator<Map<String, Object>> iter = records.iterator();
            for (Map<String, Object> rec : records) {
                Object idObj = rec.get("id");
                Integer annId = null;
                if (idObj instanceof Integer) {
                    annId = (Integer) idObj;
                } else if (idObj instanceof Long) {
                    annId = ((Long) idObj).intValue();
                }
                if (annId != null) {
                    rec.put("isRead", readIdSet.contains(annId));
                    // 过滤已被当前用户删除的公告会话
                    if (deletedIdSet.contains(String.valueOf(annId))) {
                        // 使用占位标记，后续移除
                        rec.put("__deleted__", true);
                    }
                }
            }
            // 移除标记为已删除的记录
            iter = records.iterator();
            while (iter.hasNext()) {
                Map<String, Object> rec = iter.next();
                if (Boolean.TRUE.equals(rec.get("__deleted__"))) {
                    iter.remove();
                }
            }
        }
        return Result.success(result);
    }

    /**
     * 根据ID获取公告
     * 
     * @param id 公告ID
     * @return 公告信息
     */
    @GetMapping("/{id}")
    public Result<Announcement> getAnnouncementById(@PathVariable Integer id) {
        Announcement announcement = announcementService.findById(id);
        if (announcement == null) {
            return Result.error("公告不存在");
        }
        return Result.success(announcement);
    }

    /**
     * 更新公告
     * 
     * @param id 公告ID
     * @param announcement 公告信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result<Announcement> updateAnnouncement(
            @PathVariable Integer id,
            @RequestBody @Valid Announcement announcement) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            announcement.setId(id);
            Announcement updatedAnnouncement = announcementService.update(announcement, userId);
            return Result.success(updatedAnnouncement);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除公告
     * 
     * @param id 公告ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteAnnouncement(@PathVariable Integer id) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            boolean result = announcementService.deleteById(id, userId);
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取重要公告
     * 
     * @return 重要公告列表
     */
    @GetMapping("/important")
    public Result<List<Announcement>> getImportantAnnouncements() {
        List<Announcement> announcements = announcementService.findImportant();
        return Result.success(announcements);
    }

    /**
     * 获取最新公告
     * 
     * @param limit 限制数量
     * @return 最新公告列表
     */
    @GetMapping("/latest")
    public Result<List<Announcement>> getLatestAnnouncements(
            @RequestParam(defaultValue = "5") Integer limit) {
        List<Announcement> announcements = announcementService.findLatest(limit);
        return Result.success(announcements);
    }

    /**
     * 根据标题搜索公告
     * 
     * @param title 标题关键词
     * @return 搜索结果
     */
    @GetMapping("/search")
    public Result<List<Announcement>> searchAnnouncementsByTitle(@RequestParam String title) {
        List<Announcement> announcements = announcementService.findByTitle(title);
        return Result.success(announcements);
    }

    /**
     * 获取用户创建的公告
     * 
     * @return 公告列表
     */
    @GetMapping("/created")
    public Result<List<Announcement>> getCreatedAnnouncements() {
        // 获取当前登录用户ID
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer creatorId = (Integer) claims.get("id");
        
        List<Announcement> announcements = announcementService.findByCreatorId(creatorId);
        return Result.success(announcements);
    }
    
    /**
     * 更新公告状态
     * 
     * @param id 公告ID
     * @param statusMap 包含状态信息的Map
     * @return 更新结果
     */
    @PutMapping("/{id}/status")
    public Result<Announcement> updateAnnouncementStatus(
            @PathVariable Integer id,
            @RequestBody Map<String, String> statusMap) {
        try {
            String status = statusMap.get("status");
            if (status == null) {
                return Result.error("状态不能为空");
            }
            
            Announcement updatedAnnouncement = announcementService.updateStatus(id, status);
            return Result.success(updatedAnnouncement);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}