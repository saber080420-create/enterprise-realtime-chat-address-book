package org.itheima.service.impl;

import org.itheima.mapper.ChatGroupMapper;
import org.itheima.mapper.ChatGroupMemberMapper;
import org.itheima.pojo.ChatGroup;
import org.itheima.pojo.ChatGroupMember;
import org.itheima.pojo.User;
import org.itheima.service.ChatGroupMemberService;
import org.itheima.mapper.UserMapper;
import org.itheima.service.SystemNoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.itheima.service.WebSocketService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天群组成员服务实现类
 */
@Service
public class ChatGroupMemberServiceImpl implements ChatGroupMemberService {

    @Autowired
    private ChatGroupMemberMapper chatGroupMemberMapper;
    
    @Autowired
    private ChatGroupMapper chatGroupMapper;

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SystemNoticeService systemNoticeService;

    @Override
    public ChatGroupMember findById(Integer id) {
        return chatGroupMemberMapper.findById(id);
    }

    @Override
    public List<ChatGroupMember> findByGroupId(Integer groupId) {
        return chatGroupMemberMapper.findByGroupId(groupId);
    }

    @Override
    public List<User> findMembersByGroupId(Integer groupId) {
        return chatGroupMemberMapper.findUsersByGroupId(groupId);
    }

    @Override
    public ChatGroupMember findByGroupIdAndUserId(Integer groupId, Integer userId) {
        return chatGroupMemberMapper.findByGroupIdAndUserId(groupId, userId);
    }

    @Override
    @Transactional
    public ChatGroupMember addMember(ChatGroupMember chatGroupMember) {
        // 检查群组是否存在
        ChatGroup group = chatGroupMapper.findById(chatGroupMember.getGroupId());
        if (group == null) {
            throw new RuntimeException("群组不存在");
        }
        
        // 检查用户是否已经是群成员
        ChatGroupMember existingMember = chatGroupMemberMapper.findByGroupIdAndUserId(
                chatGroupMember.getGroupId(), chatGroupMember.getUserId());
        if (existingMember != null) {
            throw new RuntimeException("用户已经是群成员");
        }
        
        // 设置加入时间和创建时间
        LocalDateTime now = LocalDateTime.now();
        chatGroupMember.setJoinTime(now);
        chatGroupMember.setCreateTime(now);
        chatGroupMember.setUpdateTime(now);
        
        // 如果没有设置角色，默认为普通成员
        if (chatGroupMember.getRole() == null) {
            chatGroupMember.setRole("member");
        }
        
        // 如果没有设置禁言状态，默认为不禁言
        if (chatGroupMember.getMute() == null) {
            chatGroupMember.setMute(false);
        }
        
        // 保存成员
        chatGroupMemberMapper.add(chatGroupMember);
        
        return chatGroupMember;
    }

    @Override
    @Transactional
    public int addMembers(Integer groupId, List<Integer> userIds, Integer operatorId) {
        // 检查群组是否存在
        ChatGroup group = chatGroupMapper.findById(groupId);
        if (group == null) {
            throw new RuntimeException("群组不存在");
        }
        
        // 放宽权限：群内任意成员均可邀请
        ChatGroupMember currentMember = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, operatorId);
        if (currentMember == null) {
            throw new RuntimeException("只有群成员可以邀请新成员");
        }
        
        // 批量添加成员
        List<ChatGroupMember> membersToAdd = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (Integer userId : userIds) {
            // 检查用户是否已经是群成员
            ChatGroupMember existingMember = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, userId);
            if (existingMember != null) {
                continue; // 跳过已经是群成员的用户
            }
            
            ChatGroupMember member = new ChatGroupMember();
            member.setGroupId(groupId);
            member.setUserId(userId);
            member.setRole("member");
            member.setMute(false);
            member.setJoinTime(now);
            member.setCreateTime(now);
            member.setUpdateTime(now);
            
            membersToAdd.add(member);
        }
        
        if (!membersToAdd.isEmpty()) {
            int added = chatGroupMemberMapper.batchAdd(membersToAdd);
            // 广播“成员加入”提醒（逐个用户）
            try {
                for (org.itheima.pojo.ChatGroupMember m : membersToAdd) {
                    Integer uid = m.getUserId();
                    String name = displayName(groupId, uid);
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("event", "group_member_joined");
                    data.put("groupId", groupId);
                    data.put("targetUserId", uid);
                    data.put("targetName", name);
                    data.put("operatorId", operatorId);
                    data.put("operatorName", displayName(groupId, operatorId));
                    data.put("time", System.currentTimeMillis());
                    webSocketService.broadcastGroupMemberLeft(data); // 复用广播接口，前端按 event 识别

                    // 系统通知：邀请入群
                    try {
                        String title = "群聊邀请";
                        String content = (displayName(groupId, operatorId) + " 邀请你加入群聊 " + (group != null ? group.getGroupName() : String.valueOf(groupId)));
                        systemNoticeService.sendNotice(uid, operatorId, "group_invited", title, content);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            return added;
        }
        
        return 0;
    }

    @Override
    @Transactional
    public ChatGroupMember updateMember(ChatGroupMember chatGroupMember, Integer operatorId) {
        // 检查成员是否存在
        ChatGroupMember existingMember = chatGroupMemberMapper.findById(chatGroupMember.getId());
        if (existingMember == null) {
            throw new RuntimeException("群成员不存在");
        }
        
        // 检查操作者是否是群主或管理员
        ChatGroupMember currentMember = chatGroupMemberMapper.findByGroupIdAndUserId(
                existingMember.getGroupId(), operatorId);
        if (currentMember == null || !("owner".equals(currentMember.getRole()) || "admin".equals(currentMember.getRole()))) {
            throw new RuntimeException("没有权限修改群成员信息");
        }
        
        // 如果是管理员，不能修改群主或其他管理员的信息
        if ("admin".equals(currentMember.getRole()) && 
                ("owner".equals(existingMember.getRole()) || "admin".equals(existingMember.getRole()))) {
            throw new RuntimeException("管理员不能修改群主或其他管理员的信息");
        }
        
        // 更新成员信息
        chatGroupMember.setUpdateTime(LocalDateTime.now());
        chatGroupMemberMapper.update(chatGroupMember);
        
        return chatGroupMember;
    }

    @Override
    @Transactional
    public boolean deleteMember(Integer id, Integer operatorId) {
        // 检查成员是否存在
        ChatGroupMember existingMember = chatGroupMemberMapper.findById(id);
        if (existingMember == null) {
            throw new RuntimeException("群成员不存在");
        }

        // 允许“本人退出群聊”（非群主）无需管理员权限
        if (existingMember.getUserId().equals(operatorId)) {
            if ("owner".equals(existingMember.getRole())) {
                throw new RuntimeException("群主不能退出群，请先转让群主");
            }
            // 先计算名称（保留 alias），再删除
            String targetName = displayName(existingMember.getGroupId(), existingMember.getUserId());
            String operatorName = displayName(existingMember.getGroupId(), operatorId);
            chatGroupMemberMapper.deleteById(id);
            // 直接发送通知
            try {
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("event", "group_member_left");
                data.put("groupId", existingMember.getGroupId());
                data.put("targetUserId", existingMember.getUserId());
                data.put("operatorId", operatorId);
                data.put("targetName", targetName);
                data.put("operatorName", operatorName);
                data.put("time", System.currentTimeMillis());
                webSocketService.broadcastGroupMemberLeft(data);
            } catch (Exception ignored) {}
            return true;
        }

        // 非本人操作：需要群主或管理员权限
        ChatGroupMember currentMember = chatGroupMemberMapper.findByGroupIdAndUserId(
                existingMember.getGroupId(), operatorId);
        if (currentMember == null || !("owner".equals(currentMember.getRole()) || "admin".equals(currentMember.getRole()))) {
            throw new RuntimeException("没有权限删除群成员");
        }
        
        // 如果是管理员，不能删除群主或其他管理员
        if ("admin".equals(currentMember.getRole()) && 
                ("owner".equals(existingMember.getRole()) || "admin".equals(existingMember.getRole()))) {
            throw new RuntimeException("管理员不能删除群主或其他管理员");
        }
        
        // 群主不能删除自己（退出群）
        if ("owner".equals(existingMember.getRole()) && existingMember.getUserId().equals(operatorId)) {
            throw new RuntimeException("群主不能退出群，请先转让群主");
        }
        
        // 先计算名称（保留 alias），再删除
        String targetName = displayName(existingMember.getGroupId(), existingMember.getUserId());
        String operatorName = displayName(existingMember.getGroupId(), operatorId);
        // 删除成员
        chatGroupMemberMapper.deleteById(id);

        // WebSocket 通知：被移出或退出 + 群内广播
        try {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            boolean selfLeave = existingMember.getUserId().equals(operatorId);
            data.put("event", selfLeave ? "group_member_left" : "group_member_removed");
            data.put("groupId", existingMember.getGroupId());
            data.put("targetUserId", existingMember.getUserId());
            data.put("operatorId", operatorId);
            // 补充名称（群内昵称优先）
            data.put("targetName", targetName);
            data.put("operatorName", operatorName);
            data.put("time", System.currentTimeMillis());
            if (selfLeave) {
                // 退出：广播通知
                webSocketService.broadcastGroupMemberLeft(data);
            } else {
                // 踢人：通知被踢者本人 + 广播给群内其他在线成员
                webSocketService.notifyGroupMemberRemoved(existingMember.getUserId(), data);
                webSocketService.broadcastGroupMemberRemoved(existingMember.getGroupId(), data, existingMember.getUserId());

                // 系统通知：被移出群聊
                try {
                    ChatGroup g = chatGroupMapper.findById(existingMember.getGroupId());
                    String title = "群聊移除";
                    String content = operatorName + " 将你移出群聊 " + (g != null ? g.getGroupName() : String.valueOf(existingMember.getGroupId()));
                    systemNoticeService.sendNotice(existingMember.getUserId(), operatorId, "group_removed", title, content);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        
        return true;
    }

    @Override
    @Transactional
    public boolean removeMember(Integer groupId, Integer userId, Integer operatorId) {
        // 检查成员是否存在
        ChatGroupMember existingMember = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, userId);
        if (existingMember == null) {
            throw new RuntimeException("群成员不存在");
        }
        // 本人退出：这里直接处理，避免走到 deleteMember 的管理员权限分支
        if (userId != null && userId.equals(operatorId)) {
            if ("owner".equals(existingMember.getRole())) {
                throw new RuntimeException("群主不能退出群，请先转让群主");
            }
            chatGroupMemberMapper.deleteById(existingMember.getId());
            try {
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("event", "group_member_left");
                data.put("groupId", existingMember.getGroupId());
                data.put("targetUserId", existingMember.getUserId());
                data.put("operatorId", operatorId);
                data.put("targetName", displayName(existingMember.getGroupId(), existingMember.getUserId()));
                data.put("operatorName", displayName(existingMember.getGroupId(), operatorId));
                data.put("time", System.currentTimeMillis());
                webSocketService.broadcastGroupMemberLeft(data);
            } catch (Exception ignored) {}
            return true;
        }

        return deleteMember(existingMember.getId(), operatorId);
    }

    private String displayName(Integer groupId, Integer userId) {
        try {
            // 1) 群内昵称 alias 优先
            ChatGroupMember m = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, userId);
            if (m != null && m.getAlias() != null && !m.getAlias().trim().isEmpty()) {
                return m.getAlias();
            }
            // 2) 用户资料：昵称 > 真实姓名 > 用户名
            User u = userMapper.findById(userId);
            if (u == null) return String.valueOf(userId);
            if (u.getNickname() != null && !u.getNickname().trim().isEmpty()) return u.getNickname();
            if (u.getRealname() != null && !u.getRealname().trim().isEmpty()) return u.getRealname();
            return u.getUsername();
        } catch (Exception e) {
            return String.valueOf(userId);
        }
    }

    // 已移除：管理员查询相关接口

    @Override
    public ChatGroupMember findOwnerByGroupId(Integer groupId) {
        return chatGroupMemberMapper.findOwnerByGroupId(groupId);
    }

    // 已移除：更新成员角色

    // 已移除：更新成员禁言状态

    // 已移除：更新成员群内昵称

    @Override
    public boolean isMember(Integer groupId, Integer userId) {
        ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, userId);
        return member != null;
    }

    @Override
    public boolean isAdminOrOwner(Integer groupId, Integer userId) {
        ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, userId);
        return member != null && ("admin".equals(member.getRole()) || "owner".equals(member.getRole()));
    }

    @Override
    public boolean isOwner(Integer groupId, Integer userId) {
        ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, userId);
        return member != null && "owner".equals(member.getRole());
    }
    
    @Override
    public String getMemberRole(Integer groupId, Integer userId) {
        ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new RuntimeException("用户不是群组成员");
        }
        return member.getRole();
    }
}