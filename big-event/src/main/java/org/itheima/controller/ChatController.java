package org.itheima.controller;

import jakarta.validation.Valid;
import org.itheima.pojo.ChatGroup;
import org.itheima.pojo.ChatMessage;
import org.itheima.pojo.Result;
import org.itheima.pojo.User;
import org.itheima.service.ChatGroupMemberService;
import org.itheima.service.ChatGroupService;
import org.itheima.service.ChatMessageService;
import org.itheima.service.GroupAnnouncementService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChatController：聊天聚合门面
 * 说明：整合原 /chat/message、/chat/group、/chat/group/member 与群公告接口到 /chat 门面；
 * 原路径保持不变，此处为等价新增。
 */
@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private ChatGroupService chatGroupService;
    @Autowired
    private ChatGroupMemberService chatGroupMemberService;
    @Autowired
    private GroupAnnouncementService groupAnnouncementService;

    // ===== 消息 =====

    @PostMapping("/message/send")
    public Result<ChatMessage> sendMessage(@RequestBody @Valid ChatMessage chatMessage) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        chatMessage.setSenderId(userId);
        ChatMessage sent = chatMessageService.sendMessage(chatMessage);
        return Result.success(sent);
    }

    @PutMapping("/message/edit/{messageId}")
    public Result<ChatMessage> editMessage(@PathVariable Long messageId, @RequestBody Map<String, String> body) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        String newContent = body.get("content");
        if (newContent == null || newContent.trim().isEmpty()) {
            return Result.error("新内容不能为空");
        }
        ChatMessage updated = chatMessageService.editMessage(messageId, currentUserId, newContent.trim());
        return Result.success(updated);
    }

    @PutMapping("/message/recall/{messageId}")
    public Result<ChatMessage> recallMessage(@PathVariable Long messageId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        ChatMessage recalled = chatMessageService.recallMessage(messageId, currentUserId);
        return Result.success(recalled);
    }

    @GetMapping("/message/private/{userId}")
    public Result<List<ChatMessage>> getSingleChatHistory(@PathVariable Integer userId,
                                                          @RequestParam(required = false, defaultValue = "15") Integer limit,
                                                          @RequestParam(required = false, defaultValue = "0") Integer offset) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<ChatMessage> messages = chatMessageService.getSingleChatHistory(currentUserId, userId, limit, offset);
        return Result.success(messages);
    }

    @GetMapping("/message/group/{groupId}")
    public Result<List<org.itheima.pojo.dto.ChatMessageDTO>> getGroupChatHistory(@PathVariable Integer groupId,
                                                                                  @RequestParam(required = false, defaultValue = "20") Integer limit,
                                                                                  @RequestParam(required = false, defaultValue = "0") Integer offset) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<org.itheima.pojo.dto.ChatMessageDTO> messages = chatMessageService.getGroupChatHistory(currentUserId, groupId, limit, offset);
        return Result.success(messages);
    }

    @PutMapping("/message/markMessageAsRead/{messageId}")
    public Result<ChatMessage> markMessageAsRead(@PathVariable Integer messageId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        boolean success = chatMessageService.markMessageAsRead(messageId.longValue(), currentUserId);
        ChatMessage message = null;
        if (success) {
            message = chatMessageService.findById(messageId.longValue());
        }
        return Result.success(message);
    }

    @PutMapping("/message/read/batch")
    public Result<Integer> markMessagesAsRead(@RequestParam(required = false) Integer senderId,
                                              @RequestParam(required = false) Integer groupId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        int count;
        if (senderId != null) {
            count = chatMessageService.markMessagesAsRead(senderId, currentUserId, "single");
        } else if (groupId != null) {
            count = chatMessageService.markMessagesAsRead(groupId, currentUserId, "group");
        } else {
            return Result.error("必须指定发送者ID或群组ID");
        }
        return Result.success(count);
    }

    @GetMapping("/message/unread/count")
    public Result<Map<String, Object>> getUnreadMessageCount() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        Long unreadCount = chatMessageService.countUnreadMessages(currentUserId);
        Map<String, Object> counts = new HashMap<>();
        counts.put("unreadCount", unreadCount);
        return Result.success(counts);
    }

    @GetMapping("/message/recent")
    public Result<List<Map<String, Object>>> getRecentChats(@RequestParam(required = false, defaultValue = "20") Integer limit) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<Map<String, Object>> recentChats = chatMessageService.getRecentChats(currentUserId, limit);
        return Result.success(recentChats);
    }

    @DeleteMapping("/message/{messageId}")
    public Result<Boolean> deleteMessage(@PathVariable Integer messageId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        boolean result = chatMessageService.deleteMessage(messageId, currentUserId);
        return Result.success(result);
    }

    // ===== 群组 =====

    @PostMapping("/group")
    public Result<ChatGroup> createGroup(@RequestBody @Valid ChatGroup chatGroup,
                                         @RequestParam(required = false) List<Integer> memberIds) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer creatorId = (Integer) claims.get("id");
        chatGroup.setCreatorId(creatorId);
        ChatGroup createdGroup = chatGroupService.createGroup(chatGroup, memberIds);
        return Result.success(createdGroup);
    }

    @GetMapping("/group")
    public Result<List<ChatGroup>> getAllGroups() {
        List<ChatGroup> groups = chatGroupService.findAll();
        return Result.success(groups);
    }

    @GetMapping("/group/{id}")
    public Result<ChatGroup> getGroupById(@PathVariable Integer id) {
        ChatGroup group = chatGroupService.findById(id);
        if (group == null) return Result.error("群组不存在");
        return Result.success(group);
    }

    @GetMapping("/group/my")
    public Result<List<ChatGroup>> getMyGroups() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        List<ChatGroup> groups = chatGroupService.findByUserId(userId);
        return Result.success(groups);
    }

    @GetMapping("/group/created")
    public Result<List<ChatGroup>> getCreatedGroups() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer creatorId = (Integer) claims.get("id");
        List<ChatGroup> groups = chatGroupService.findByCreatorId(creatorId);
        return Result.success(groups);
    }

    @PutMapping("/group/{id}")
    public Result<ChatGroup> updateGroup(@PathVariable Integer id, @RequestBody @Valid ChatGroup chatGroup) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        chatGroup.setId(id);
        ChatGroup updatedGroup = chatGroupService.update(chatGroup, userId);
        return Result.success(updatedGroup);
    }

    @DeleteMapping("/group/{id}")
    public Result<Boolean> deleteGroup(@PathVariable Integer id) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        boolean result = chatGroupService.deleteById(id, userId);
        return Result.success(result);
    }

    @GetMapping("/group/search")
    public Result<List<ChatGroup>> searchGroupsByName(@RequestParam String name) {
        List<ChatGroup> groups = chatGroupService.findByName(name);
        return Result.success(groups);
    }

    @GetMapping("/group/withMemberCount")
    public Result<List<ChatGroup>> getGroupsWithMemberCount() {
        List<ChatGroup> result = chatGroupService.findAllWithMemberCount();
        return Result.success(result);
    }

    // ===== 群成员 =====

    @GetMapping("/member/list/{groupId}")
    public Result<List<User>> getGroupMembers(@PathVariable Integer groupId) {
        List<User> members = chatGroupMemberService.findMembersByGroupId(groupId);
        return Result.success(members);
    }

    @PostMapping("/member/add/{groupId}")
    public Result<Integer> addGroupMembers(@PathVariable Integer groupId, @RequestBody List<Integer> userIds) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer operatorId = (Integer) claims.get("id");
        int addedCount = chatGroupMemberService.addMembers(groupId, userIds, operatorId);
        return Result.success(addedCount);
    }

    @DeleteMapping("/member/remove/{groupId}/{userId}")
    public Result<Boolean> removeGroupMember(@PathVariable Integer groupId, @PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer operatorId = (Integer) claims.get("id");
        boolean result = chatGroupMemberService.removeMember(groupId, userId, operatorId);
        return Result.success(result);
    }

    @PostMapping("/member/leave/{groupId}")
    public Result<Boolean> leaveGroup(@PathVariable Integer groupId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        boolean result = chatGroupMemberService.removeMember(groupId, userId, userId);
        return Result.success(result);
    }

    @GetMapping("/member/check/{groupId}")
    public Result<Boolean> checkMembership(@PathVariable Integer groupId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        boolean isMember = chatGroupMemberService.isMember(groupId, userId);
        return Result.success(isMember);
    }

    @GetMapping("/member/role/{groupId}")
    public Result<String> getMemberRole(@PathVariable Integer groupId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        String role = chatGroupMemberService.getMemberRole(groupId, userId);
        return Result.success(role);
    }

    // ===== 群公告 =====

    @PostMapping("/group/{groupId}/announcement")
    public Result<Map<String, Object>> publishGroupAnnouncement(@PathVariable Integer groupId, @RequestBody Map<String, String> body) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        String title = body.getOrDefault("title", "群公告");
        String content = body.getOrDefault("content", "");
        if (content == null || content.trim().isEmpty()) {
            return Result.error("公告内容不能为空");
        }
        org.itheima.pojo.GroupAnnouncement ga = groupAnnouncementService.publish(groupId, userId, title, content);
        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("id", ga.getId());
        return Result.success(resp);
    }

    @GetMapping("/group/{groupId}/announcement/list")
    public Result<List<Map<String, Object>>> listGroupAnnouncements(@PathVariable Integer groupId, @RequestParam(required = false, defaultValue = "20") Integer limit) {
        List<Map<String, Object>> list = groupAnnouncementService.listLatest(groupId, limit);
        return Result.success(list);
    }

    @PostMapping("/group/{groupId}/announcement/{id}/withdraw")
    public Result<Boolean> withdrawGroupAnnouncement(@PathVariable Integer groupId, @PathVariable Integer id) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        boolean ok = groupAnnouncementService.withdraw(id, userId);
        return Result.success(ok);
    }

    @PostMapping("/announcement/{id}/read")
    public Result<Boolean> markGroupAnnouncementRead(@PathVariable Integer id) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        boolean ok = groupAnnouncementService.markRead(id, userId);
        return Result.success(ok);
    }

    @GetMapping("/announcement/{id}/readers")
    public Result<List<Map<String, Object>>> listGroupAnnouncementReaders(@PathVariable Integer id) {
        List<Map<String, Object>> readers = groupAnnouncementService.listReaders(id);
        return Result.success(readers);
    }

    @GetMapping("/group/{groupId}/announcement/unread-count")
    public Result<Long> getGroupAnnouncementUnreadCount(@PathVariable Integer groupId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        Long cnt = groupAnnouncementService.countUnread(groupId, userId);
        return Result.success(cnt);
    }
}


