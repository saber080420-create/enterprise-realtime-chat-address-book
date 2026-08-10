package org.itheima.service.impl;

import org.itheima.mapper.ChatGroupMapper;
import org.itheima.mapper.ChatGroupMemberMapper;
import org.itheima.pojo.ChatGroup;
import org.itheima.pojo.ChatGroupMember;
import org.itheima.service.ChatGroupService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.itheima.service.WebSocketService;
import org.itheima.service.SystemNoticeService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天群组服务实现类
 */
@Service
public class ChatGroupServiceImpl implements ChatGroupService {

    @Autowired
    private ChatGroupMapper chatGroupMapper;
    
    @Autowired
    private ChatGroupMemberMapper chatGroupMemberMapper;

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private SystemNoticeService systemNoticeService;

    @Override
    public ChatGroup findById(Integer id) {
        return chatGroupMapper.findById(id);
    }

    @Override
    public List<ChatGroup> findAll() {
        return chatGroupMapper.findAll();
    }

    @Override
    public List<ChatGroup> findByCreatorId(Integer creatorId) {
        return chatGroupMapper.findByCreatorId(creatorId);
    }

    @Override
    public List<ChatGroup> findByUserId(Integer userId) {
        return chatGroupMapper.findByUserId(userId);
    }

    @Override
    @Transactional
    public ChatGroup createGroup(ChatGroup chatGroup, List<Integer> memberIds) {
        // 检查群组名称是否已存在
        List<ChatGroup> existingGroups = chatGroupMapper.findByName(chatGroup.getGroupName());
        if (!existingGroups.isEmpty()) {
            // 检查是否有完全匹配的群组名称
            for (ChatGroup group : existingGroups) {
                if (group.getGroupName().equals(chatGroup.getGroupName())) {
                    throw new RuntimeException("群组名称已存在");
                }
            }
        }
        
        // 获取当前登录用户ID
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        // 设置创建者ID和创建时间
        chatGroup.setCreatorId(userId);
        LocalDateTime now = LocalDateTime.now();
        chatGroup.setCreateTime(now);
        chatGroup.setUpdateTime(now);
        
        // 保存群组
        chatGroupMapper.add(chatGroup);
        
        // 添加创建者为群主
        ChatGroupMember ownerMember = new ChatGroupMember();
        ownerMember.setGroupId(chatGroup.getId());
        ownerMember.setUserId(userId);
        ownerMember.setRole("owner");
        ownerMember.setMute(false);
        ownerMember.setJoinTime(now);
        ownerMember.setCreateTime(now);
        ownerMember.setUpdateTime(now);
        
        chatGroupMemberMapper.add(ownerMember);
        
        // 添加其他成员
        if (memberIds != null && !memberIds.isEmpty()) {
            for (Integer memberId : memberIds) {
                // 跳过创建者自己
                if (memberId.equals(userId)) {
                    continue;
                }
                
                ChatGroupMember member = new ChatGroupMember();
                member.setGroupId(chatGroup.getId());
                member.setUserId(memberId);
                member.setRole("member");
                member.setMute(false);
                member.setJoinTime(now);
                member.setCreateTime(now);
                member.setUpdateTime(now);
                
                chatGroupMemberMapper.add(member);
            }
        }
        
        return chatGroup;
    }

    @Override
    @Transactional
    public ChatGroup updateGroup(ChatGroup chatGroup) {
        // 检查群组是否存在
        ChatGroup existingGroup = chatGroupMapper.findById(chatGroup.getId());
        if (existingGroup == null) {
            throw new RuntimeException("群组不存在");
        }
        
        // 获取当前登录用户ID
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        // 检查是否是群主或管理员
        ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(chatGroup.getId(), userId);
        if (member == null || !("owner".equals(member.getRole()) || "admin".equals(member.getRole()))) {
            throw new RuntimeException("没有权限修改群组信息");
        }
        
        // 检查群组名称是否已被其他群组使用
        List<ChatGroup> groupsWithSameName = chatGroupMapper.findByName(chatGroup.getGroupName());
        if (!groupsWithSameName.isEmpty()) {
            // 检查是否有其他群组使用了完全相同的名称
            for (ChatGroup group : groupsWithSameName) {
                if (group.getGroupName().equals(chatGroup.getGroupName()) && !group.getId().equals(chatGroup.getId())) {
                    throw new RuntimeException("群组名称已存在");
                }
            }
        }
        
        // 更新群组信息
        chatGroup.setUpdateTime(LocalDateTime.now());
        chatGroupMapper.update(chatGroup);
        
        return chatGroup;
    }
    
    @Override
    @Transactional
    public ChatGroup update(ChatGroup chatGroup, Integer userId) {
        // 检查群组是否存在
        ChatGroup existingGroup = chatGroupMapper.findById(chatGroup.getId());
        if (existingGroup == null) {
            throw new RuntimeException("群组不存在");
        }
        
        // 检查是否是群主或管理员
        ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(chatGroup.getId(), userId);
        if (member == null || !("owner".equals(member.getRole()) || "admin".equals(member.getRole()))) {
            throw new RuntimeException("没有权限修改群组信息");
        }
        
        // 检查群组名称是否已被其他群组使用
        List<ChatGroup> groupsWithSameName = chatGroupMapper.findByName(chatGroup.getGroupName());
        if (!groupsWithSameName.isEmpty()) {
            // 检查是否有其他群组使用了完全相同的名称
            for (ChatGroup group : groupsWithSameName) {
                if (group.getGroupName().equals(chatGroup.getGroupName()) && !group.getId().equals(chatGroup.getId())) {
                    throw new RuntimeException("群组名称已存在");
                }
            }
        }
        
        // 更新群组信息
        chatGroup.setUpdateTime(LocalDateTime.now());
        chatGroupMapper.update(chatGroup);
        
        return chatGroup;
    }

    @Override
    @Transactional
    public boolean deleteGroup(Integer id) {
        // 检查群组是否存在
        ChatGroup existingGroup = chatGroupMapper.findById(id);
        if (existingGroup == null) {
            throw new RuntimeException("群组不存在");
        }
        
        // 获取当前登录用户ID
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        // 检查是否是群主
        ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(id, userId);
        if (member == null || !"owner".equals(member.getRole())) {
            throw new RuntimeException("只有群主才能删除群组");
        }
        
        // 删除群组成员
        chatGroupMemberMapper.deleteByGroupId(id);
        
        // 删除群组
        chatGroupMapper.deleteById(id);
        
        return true;
    }
    
    @Override
    @Transactional
    public boolean deleteById(Integer id, Integer userId) {
        // 检查群组是否存在
        ChatGroup existingGroup = chatGroupMapper.findById(id);
        if (existingGroup == null) {
            throw new RuntimeException("群组不存在");
        }
        
        // 检查是否是群主
        ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(id, userId);
        if (member == null || !"owner".equals(member.getRole())) {
            throw new RuntimeException("只有群主才能删除群组");
        }
        
        // 删除群组成员
        chatGroupMemberMapper.deleteByGroupId(id);
        
        // 删除群组
        chatGroupMapper.deleteById(id);
        
        // WS 通知：群聊已解散
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("event", "group_disbanded");
            payload.put("groupId", id);
            payload.put("operatorId", userId);
            payload.put("time", System.currentTimeMillis());
            webSocketService.broadcastGroupDisband(id, payload);
        } catch (Exception ignored) {}

        // 系统通知：给原群成员发送“群解散”
        try {
            List<ChatGroupMember> members = chatGroupMemberMapper.findByGroupId(id);
            String title = "群聊解散";
            ChatGroup g = existingGroup;
            String content = "群聊 " + (g != null ? g.getGroupName() : String.valueOf(id)) + " 已被群主解散";
            if (members != null) {
                for (ChatGroupMember m : members) {
                    if (m.getUserId() != null) {
                        systemNoticeService.sendNotice(m.getUserId(), userId, "group_disbanded", title, content);
                    }
                }
            }
        } catch (Exception ignored) {}

        return true;
    }

    @Override
    public List<ChatGroup> findByName(String groupName) {
        return chatGroupMapper.findByName(groupName);
    }

    @Override
    public Integer countGroupMembers(Integer groupId) {
        return chatGroupMemberMapper.countByGroupId(groupId);
    }

    @Override
    public List<ChatGroup> findAllWithMemberCount() {
        return chatGroupMapper.findAllWithMemberCount();
    }

    @Override
    public List<ChatGroup> searchGroups(String keyword, Integer limit, Integer offset) {
        return chatGroupMapper.searchGroups(keyword, limit, offset);
    }
}