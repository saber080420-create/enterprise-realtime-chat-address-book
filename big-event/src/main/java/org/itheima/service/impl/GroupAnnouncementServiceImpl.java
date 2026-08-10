package org.itheima.service.impl;

import org.itheima.mapper.ChatGroupMemberMapper;
import org.itheima.mapper.GroupAnnouncementMapper;
import org.itheima.pojo.GroupAnnouncement;
import org.itheima.service.GroupAnnouncementService;
import org.itheima.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroupAnnouncementServiceImpl implements GroupAnnouncementService {

    @Autowired
    private GroupAnnouncementMapper groupAnnouncementMapper;

    @Autowired
    private ChatGroupMemberMapper chatGroupMemberMapper;

    @Autowired
    private WebSocketService webSocketService;

    @Override
    public GroupAnnouncement publish(Integer groupId, Integer publisherId, String title, String content) {
        // 权限校验：必须是群主
        org.itheima.pojo.ChatGroupMember m = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, publisherId);
        if (m == null || (m.getRole() == null || !"owner".equals(m.getRole()))) {
            throw new RuntimeException("只有群主可以发布群公告");
        }
        GroupAnnouncement ga = new GroupAnnouncement();
        ga.setGroupId(groupId);
        ga.setPublisherId(publisherId);
        ga.setTitle(title);
        ga.setContent(content);
        ga.setPublishTime(LocalDateTime.now());
        ga.setCreateTime(LocalDateTime.now());
        ga.setUpdateTime(LocalDateTime.now());
        groupAnnouncementMapper.insert(ga);

        // WS 广播给群成员（notification 事件）
        Map<String, Object> data = new HashMap<>();
        data.put("event", "group_announcement_published");
        data.put("groupId", groupId);
        data.put("title", title);
        data.put("publisherId", publisherId);
        webSocketService.broadcastGroupMemberLeft(data); // 复用 notification 广播
        return ga;
    }

    @Override
    public List<Map<String, Object>> listLatest(Integer groupId, Integer limit) {
        if (limit == null || limit <= 0) limit = 20;
        // 权限：仅群成员可查看群公告
        // 需要当前登录用户ID，这里通过 ThreadLocal 获取
        try {
            java.util.Map<String, Object> claims = org.itheima.utils.ThreadLocalUtil.get();
            Integer currentUserId = (Integer) claims.get("id");
            org.itheima.pojo.ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, currentUserId);
            if (member == null) {
                throw new RuntimeException("您不是群成员，无法查看群公告");
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException("您不是群成员，无法查看群公告");
        }
        return groupAnnouncementMapper.findLatestByGroupId(groupId, limit);
    }

    @Override
    public boolean withdraw(Integer announcementId, Integer operatorId) {
        // 严格校验：只有该群群主可撤回
        org.itheima.pojo.GroupAnnouncement ga = groupAnnouncementMapper.findById(announcementId);
        if (ga == null) throw new RuntimeException("公告不存在");
        org.itheima.pojo.ChatGroupMember m = chatGroupMemberMapper.findByGroupIdAndUserId(ga.getGroupId(), operatorId);
        if (m == null || m.getRole() == null || !"owner".equals(m.getRole())) {
            throw new RuntimeException("只有群主可以撤回公告");
        }
        int c = groupAnnouncementMapper.withdraw(announcementId);
        if (c > 0) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("event", "group_announcement_withdrawn");
            data.put("groupId", ga.getGroupId());
            data.put("announcementId", announcementId);
            // 使用 notification 通道按群广播
            webSocketService.broadcastNotification(data);
            return true;
        }
        return false;
    }

    @Override
    public boolean markRead(Integer announcementId, Integer userId) {
        return groupAnnouncementMapper.markRead(announcementId, userId) > 0;
    }

    @Override
    public List<Map<String, Object>> listReaders(Integer announcementId) {
        // 仅群主可查看已读名单
        GroupAnnouncement ga = groupAnnouncementMapper.findById(announcementId);
        if (ga == null) throw new RuntimeException("公告不存在");
        java.util.Map<String, Object> claims = org.itheima.utils.ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        org.itheima.pojo.ChatGroupMember m = chatGroupMemberMapper.findByGroupIdAndUserId(ga.getGroupId(), currentUserId);
        if (m == null || m.getRole() == null || !"owner".equals(m.getRole())) {
            throw new RuntimeException("只有群主可以查看已读名单");
        }
        return groupAnnouncementMapper.listReaders(announcementId);
    }

    @Override
    public Long countUnread(Integer groupId, Integer userId) {
        return groupAnnouncementMapper.countUnread(groupId, userId);
    }
}


