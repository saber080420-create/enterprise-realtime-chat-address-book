package org.itheima.controller;

import org.itheima.pojo.AnnouncementReadStatus;
import org.itheima.pojo.Result;
import org.itheima.service.AnnouncementReadStatusService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公告阅读状态控制器
 */
@RestController
@RequestMapping("/announcement/read-status")
@Validated
public class AnnouncementReadStatusController {

    @Autowired
    private AnnouncementReadStatusService announcementReadStatusService;

    @Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /**
     * 标记公告为已读
     * 
     * @param announcementId 公告ID
     * @return 标记结果
     */
    @PostMapping("/mark-read/{announcementId}")
    public Result<AnnouncementReadStatus> markAsRead(@PathVariable Integer announcementId) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            AnnouncementReadStatus readStatus = announcementReadStatusService.markAsRead(announcementId, userId);
            return Result.success(readStatus);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量标记公告为已读
     * 
     * @param announcementIds 公告ID列表
     * @return 标记结果
     */
    @PostMapping("/mark-read-batch")
    public Result<Boolean> markAsReadBatch(@RequestBody List<Integer> announcementIds) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            boolean result = announcementReadStatusService.markAsReadBatch(announcementIds, userId);
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取公告已读状态
     * 
     * @param announcementId 公告ID
     * @return 已读状态
     */
    @GetMapping("/status/{announcementId}")
    public Result<Boolean> getReadStatus(@PathVariable Integer announcementId) {
        // 获取当前登录用户ID
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        
        AnnouncementReadStatus readStatus = announcementReadStatusService.findByAnnouncementIdAndUserId(announcementId, userId);
        boolean isRead = (readStatus != null);
        return Result.success(isRead);
    }

    /**
     * 获取用户未读公告数量
     * 
     * @return 未读数量
     */
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount() {
        // 获取当前登录用户ID
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        try {
            // 拿到未读ID列表
            List<Integer> unreadList = announcementReadStatusService.findUnreadAnnouncementIdsByUserId(userId);
            // 过滤掉“我已删除的公告会话”
            if (stringRedisTemplate != null) {
                String key = "user:ann:deleted:" + userId;
                java.util.Set<String> deleted = stringRedisTemplate.opsForSet().members(key);
                if (deleted != null && !deleted.isEmpty()) {
                    unreadList.removeIf(id -> deleted.contains(String.valueOf(id)));
                }
            }
            return Result.success(unreadList.size());
        } catch (Exception e) {
            // 降级：若Redis异常，返回数据库统计值
            int count = announcementReadStatusService.countUnreadByUserId(userId);
            return Result.success(count);
        }
    }

    /**
     * 获取公告已读人数
     * 
     * @param announcementId 公告ID
     * @return 已读人数
     */
    @GetMapping("/read-count/{announcementId}")
    public Result<Integer> getReadCount(@PathVariable Integer announcementId) {
        int count = announcementReadStatusService.countReadByAnnouncementId(announcementId);
        return Result.success(count);
    }

    /**
     * 获取用户已读公告列表
     * 
     * @return 已读公告ID列表
     */
    @GetMapping("/read-list")
    public Result<List<Integer>> getReadList() {
        // 获取当前登录用户ID
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        
        List<Integer> readList = announcementReadStatusService.findReadAnnouncementIdsByUserId(userId);
        return Result.success(readList);
    }

    /**
     * 获取用户未读公告列表
     * 
     * @return 未读公告ID列表
     */
    @GetMapping("/unread-list")
    public Result<List<Integer>> getUnreadList() {
        // 获取当前登录用户ID
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        
        List<Integer> unreadList = announcementReadStatusService.findUnreadAnnouncementIdsByUserId(userId);
        return Result.success(unreadList);
    }

    /**
     * 删除已读公告会话（仅删除当前用户对此公告的阅读记录与本地会话消息，不影响公告本身）
     * 仅允许删除已读；未读则返回错误
     */
    @DeleteMapping("/delete-read/{announcementId}")
    public Result<Boolean> deleteReadConversation(@PathVariable Integer announcementId) {
        try {
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            AnnouncementReadStatus rs = announcementReadStatusService.findByAnnouncementIdAndUserId(announcementId, userId);
            if (rs == null || rs.getIsRead() == null || !rs.getIsRead()) {
                return Result.error("仅支持删除已读公告会话");
            }
            announcementReadStatusService.deleteById(rs.getId());
            // 记录一次“软删除”标记，防止刷新后重新出现
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
    
    /**
     * 获取公告已读用户详情列表
     * 
     * @param announcementId 公告ID
     * @return 已读用户详情列表
     */
    @GetMapping("/read-users/{announcementId}")
    public Result<List<Map<String, Object>>> getReadUsers(@PathVariable Integer announcementId) {
        try {
            List<Map<String, Object>> readUsers = announcementReadStatusService.findReadUsersByAnnouncementId(announcementId);
            return Result.success(readUsers);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}