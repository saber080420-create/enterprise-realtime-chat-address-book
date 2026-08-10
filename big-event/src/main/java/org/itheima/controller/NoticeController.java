package org.itheima.controller;

import jakarta.validation.Valid;
import org.itheima.pojo.Announcement;
import org.itheima.pojo.AnnouncementReadStatus;
import org.itheima.pojo.Result;
import org.itheima.pojo.SystemNotice;
import org.itheima.service.AnnouncementReadStatusService;
import org.itheima.service.AnnouncementService;
import org.itheima.service.SystemNoticeService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * NoticeController：系统公告 + 系统通知 门面
 * 说明：仅新增 /notice 命名空间；原 /announcement 与 /systemNotice 路径保持可用。
 */
@RestController
@RequestMapping("/notice")
@Validated
public class NoticeController {

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private AnnouncementReadStatusService announcementReadStatusService;

    @Autowired
    private SystemNoticeService systemNoticeService;

    @Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    // ===== 系统公告（等价于 /announcement/**） =====

    @PostMapping
    public Result<Announcement> createAnnouncement(@RequestBody @Valid Announcement announcement) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer publisherId = (Integer) claims.get("id");
        String userRole = (String) claims.get("role");
        Integer departmentId = (Integer) claims.get("departmentId");

        if (announcement.getType() == null || "company".equals(announcement.getType())) {
            if (!"system_admin".equals(userRole)) {
                return Result.error("权限不足，只有系统管理员可以发布全公司公告");
            }
        } else if ("department".equals(announcement.getType())) {
            if (!"system_admin".equals(userRole) && !"department_admin".equals(userRole)) {
                return Result.error("权限不足，只有系统管理员和部门管理员可以发布部门公告");
            }
            if ("department_admin".equals(userRole) &&
                    (announcement.getDepartmentId() == null || !announcement.getDepartmentId().equals(departmentId))) {
                announcement.setDepartmentId(departmentId);
            }
        }
        announcement.setPublisherId(publisherId);
        Announcement created = announcementService.add(announcement);
        return Result.success(created);
    }

    @GetMapping
    public Result<Map<String, Object>> getAllAnnouncements(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type) {

        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        String userRole = (String) claims.get("role");
        Integer departmentId = (Integer) claims.get("departmentId");

        Map<String, Object> result;
        if ("system_admin".equals(userRole)) {
            result = announcementService.findByPage(page, pageSize, type, null);
        } else {
            result = announcementService.findByPage(page, pageSize, type, departmentId);
        }

        if (result != null && result.get("records") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
            List<Integer> readIds = announcementReadStatusService.findReadAnnouncementIdsByUserId(userId);
            java.util.Set<Integer> readIdSet = new java.util.HashSet<>(readIds);
            java.util.Iterator<Map<String, Object>> iter;
            iter = records.iterator();
            while (iter.hasNext()) {
                Map<String, Object> rec = iter.next();
                Object idObj = rec.get("id");
                Integer annId = null;
                if (idObj instanceof Integer) {
                    annId = (Integer) idObj;
                } else if (idObj instanceof Long) {
                    annId = ((Long) idObj).intValue();
                }
                if (annId != null) {
                    rec.put("isRead", readIdSet.contains(annId));
                }
            }
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Announcement> getAnnouncementById(@PathVariable Integer id) {
        Announcement announcement = announcementService.findById(id);
        if (announcement == null) {
            return Result.error("公告不存在");
        }
        return Result.success(announcement);
    }

    @PutMapping("/{id}")
    public Result<Announcement> updateAnnouncement(@PathVariable Integer id,
                                                   @RequestBody @Valid Announcement announcement) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        announcement.setId(id);
        Announcement updated = announcementService.update(announcement, userId);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteAnnouncement(@PathVariable Integer id) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        boolean ok = announcementService.deleteById(id, userId);
        return Result.success(ok);
    }

    @GetMapping("/important")
    public Result<List<Announcement>> getImportantAnnouncements() {
        List<Announcement> list = announcementService.findImportant();
        return Result.success(list);
    }

    @GetMapping("/latest")
    public Result<List<Announcement>> getLatestAnnouncements(@RequestParam(defaultValue = "5") Integer limit) {
        List<Announcement> list = announcementService.findLatest(limit);
        return Result.success(list);
    }

    @GetMapping("/search")
    public Result<List<Announcement>> searchAnnouncementsByTitle(@RequestParam String title) {
        List<Announcement> list = announcementService.findByTitle(title);
        return Result.success(list);
    }

    @GetMapping("/created")
    public Result<List<Announcement>> getCreatedAnnouncements() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer creatorId = (Integer) claims.get("id");
        List<Announcement> list = announcementService.findByCreatorId(creatorId);
        return Result.success(list);
    }

    @PutMapping("/{id}/status")
    public Result<Announcement> updateAnnouncementStatus(@PathVariable Integer id,
                                                         @RequestBody Map<String, String> statusMap) {
        String status = statusMap.get("status");
        if (status == null) {
            return Result.error("状态不能为空");
        }
        Announcement updated = announcementService.updateStatus(id, status);
        return Result.success(updated);
    }

    // ===== 公告阅读状态（等价于 /announcement/read-status/**） =====

    @PostMapping("/read/mark-read/{announcementId}")
    public Result<AnnouncementReadStatus> markAsRead(@PathVariable Integer announcementId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        AnnouncementReadStatus rs = announcementReadStatusService.markAsRead(announcementId, userId);
        return Result.success(rs);
    }

    @PostMapping("/read/mark-read-batch")
    public Result<Boolean> markAsReadBatch(@RequestBody List<Integer> announcementIds) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        boolean ok = announcementReadStatusService.markAsReadBatch(announcementIds, userId);
        return Result.success(ok);
    }

    @GetMapping("/read/status/{announcementId}")
    public Result<Boolean> getReadStatus(@PathVariable Integer announcementId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        AnnouncementReadStatus rs = announcementReadStatusService.findByAnnouncementIdAndUserId(announcementId, userId);
        return Result.success(rs != null);
    }

    @GetMapping("/read/unread-count")
    public Result<Integer> getUnreadCount() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        int count = announcementReadStatusService.countUnreadByUserId(userId);
        return Result.success(count);
    }

    @GetMapping("/read/read-count/{announcementId}")
    public Result<Integer> getReadCount(@PathVariable Integer announcementId) {
        int count = announcementReadStatusService.countReadByAnnouncementId(announcementId);
        return Result.success(count);
    }

    @GetMapping("/read/read-users/{announcementId}")
    public Result<java.util.List<java.util.Map<String, Object>>> getReadUsers(@PathVariable Integer announcementId) {
        java.util.List<java.util.Map<String, Object>> readUsers = announcementReadStatusService.findReadUsersByAnnouncementId(announcementId);
        return Result.success(readUsers);
    }

    @GetMapping("/read/read-list")
    public Result<List<Integer>> getReadList() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        List<Integer> list = announcementReadStatusService.findReadAnnouncementIdsByUserId(userId);
        return Result.success(list);
    }

    @GetMapping("/read/unread-list")
    public Result<List<Integer>> getUnreadList() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        List<Integer> list = announcementReadStatusService.findUnreadAnnouncementIdsByUserId(userId);
        return Result.success(list);
    }

    /**
     * 删除“我的公告会话”（仅删除当前用户的已读记录，不影响公告本身）
     */
    @DeleteMapping("/read/delete-read/{announcementId}")
    public Result<Boolean> deleteReadConversation(@PathVariable Integer announcementId) {
        try {
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            AnnouncementReadStatus rs = announcementReadStatusService.findByAnnouncementIdAndUserId(announcementId, userId);
            if (rs == null || rs.getIsRead() == null || !rs.getIsRead()) {
                return Result.error("仅支持删除已读公告会话");
            }
            announcementReadStatusService.deleteById(rs.getId());
            try {
                if (stringRedisTemplate != null) {
                    String key = "user:ann:deleted:" + userId;
                    stringRedisTemplate.opsForSet().add(key, String.valueOf(announcementId));
                }
            } catch (Exception ignored) {}
            return Result.success(true);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // ===== 系统通知（等价于 /systemNotice/**） =====

    @GetMapping("/inbox/list")
    public Result<List<SystemNotice>> listSystemNotices(@RequestParam(defaultValue = "20") Integer limit,
                                           @RequestParam(defaultValue = "0") Integer offset) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        List<SystemNotice> list = systemNoticeService.listByUser(userId, limit, offset);
        return Result.success(list);
    }

    @GetMapping("/inbox/unreadCount")
    public Result<Integer> unreadSystemNoticeCount() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        Integer count = systemNoticeService.countUnread(userId);
        return Result.success(count);
    }

    @PostMapping("/inbox/markRead/{id}")
    public Result<Boolean> markSystemNoticeRead(@PathVariable Integer id) {
        systemNoticeService.markRead(id);
        return Result.success(true);
    }

    @PostMapping("/inbox/markAllRead")
    public Result<Boolean> markAllSystemNoticesRead() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        systemNoticeService.markAllRead(userId);
        return Result.success(true);
    }

    @DeleteMapping("/inbox/delete/{id}")
    public Result<Boolean> deleteSystemNotice(@PathVariable Integer id) {
        boolean ok = systemNoticeService.deleteIfRead(id);
        if (!ok) return Result.error("仅支持删除已读通知");
        return Result.success(true);
    }
}


