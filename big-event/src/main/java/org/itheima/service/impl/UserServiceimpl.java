package org.itheima.service.impl;

import org.apache.ibatis.annotations.Mapper;
import org.itheima.mapper.UserMapper;
import org.itheima.mapper.DepartmentMapper;
import org.itheima.mapper.ChatMessageMapper;
import org.itheima.mapper.FrequentContactMapper;
import org.itheima.mapper.ChatGroupMemberMapper;
import org.itheima.mapper.AnnouncementReadStatusMapper;
import org.itheima.mapper.UserActivityMapper;
import org.itheima.pojo.User;
import org.itheima.pojo.Department;
import org.itheima.pojo.UserActivity;
import org.itheima.service.UserService;
import org.itheima.service.UserActivityService;
import org.itheima.utils.Md5Util;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceimpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private DepartmentMapper departmentMapper;
    
    @Autowired
    private UserActivityService userActivityService;
    
    // 新增：注入用于级联删除的Mapper
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    
    @Autowired
    private FrequentContactMapper frequentContactMapper;
    
    @Autowired
    private ChatGroupMemberMapper chatGroupMemberMapper;
    
    @Autowired
    private AnnouncementReadStatusMapper announcementReadStatusMapper;
    
    @Autowired
    private UserActivityMapper userActivityMapper;

    @Override
    public User findById(Integer id) {
        return userMapper.findById(id);
    }

    @Override
    public User findByUserName(String username) {
        User u=userMapper.findByUserName(username);
        return u;
    }

    @Override
    public void register(String username, String password, Integer departmentId) {
        // 加密密码
        String md5String = Md5Util.getMD5String(password);
        // 创建User对象
        User user = new User();
        user.setUsername(username);
        user.setPassword(md5String);
        user.setDepartmentId(departmentId);
        user.setRole("employee"); // 默认为普通员工角色
        // 添加用户
        userMapper.add(user);
    }

    @Override
    public void update(User user) {
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
    }

    @Override
    public void updateAvatar(String avatarUrl) {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer id = (Integer) map.get("id");
        userMapper.updateAvatar(avatarUrl,id);
    }

    @Override
    public void updatePwd(String newPwd) {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer id = (Integer) map.get("id");
        userMapper.updatePwd(Md5Util.getMD5String(newPwd),id);
    }
    
    @Override
    public void updateRealname(String realname) {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer id = (Integer) map.get("id");
        userMapper.updateRealname(realname, id);
    }

    @Override
    public void updateNickname(String nickname) {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer id = (Integer) map.get("id");
        userMapper.updateNickname(nickname, id);
    }
    
    @Override
    public void updateEmail(String email) {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer id = (Integer) map.get("id");
        userMapper.updateEmail(email, id);
    }

    /**
     * 更新当前登录用户的电话
     * 从线程上下文中获取当前用户ID并更新其电话
     *
     * @param phone 电话号码
     */
    @Override
    public void updatePhone(String phone) {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer id = (Integer) map.get("id");
        userMapper.updatePhone(phone, id);
    }

    /**
     * 更新指定用户的角色
     * 注意：权限校验在控制器层完成，这里直接调用Mapper
     *
     * @param userId 用户ID
     * @param role 角色
     */
    @Override
    public void updateRole(Integer userId, String role) {
        userMapper.updateRole(userId, role);
    }

    /**
     * 更新指定用户的状态（启用/禁用）
     * 注意：权限校验在控制器层完成，这里直接调用Mapper
     *
     * @param userId 用户ID
     * @param status 状态
     */
    @Override
    public void updateStatus(Integer userId, String status) {
        userMapper.updateStatus(userId, status);
    }

    /**
     * 更新指定用户的部门
     * 注意：权限校验在控制器层完成，这里直接调用Mapper
     *
     * @param userId 用户ID
     * @param departmentId 部门ID
     */
    @Override
    public void updateDepartment(Integer userId, Integer departmentId) {
        userMapper.updateDepartment(userId, departmentId);
    }

    /**
     * 更新指定用户的职位
     * 注意：权限校验在控制器层完成，这里直接调用Mapper
     *
     * @param userId 用户ID
     * @param position 职位
     */
    @Override
    public void updatePosition(Integer userId, String position) {
        userMapper.updatePosition(userId, position);
    }

    /**
     * 删除用户（包含级联删除相关数据）
     * 删除用户时会自动清理以下相关数据：
     * 1. 该用户发送或接收的所有聊天消息
     * 2. 该用户的常用联系人关系（作为owner或contact）
     * 3. 该用户的群组成员关系
     * 4. 该用户的公告阅读状态
     * 5. 该用户的活动记录
     * 6. 最后删除用户自身记录
     *
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Integer userId) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        try {
            // 1. 删除该用户相关的聊天消息
            deleteChatMessagesByUserId(userId);
            
            // 2. 删除该用户的常用联系人关系（作为owner）
            frequentContactMapper.deleteByUserId(userId);
            
            // 3. 删除其他用户对该用户的常用联系人关系（作为contact）
            deleteFrequentContactsByContactId(userId);
            
            // 4. 删除该用户的群组成员关系
            chatGroupMemberMapper.deleteByUserId(userId);
            
            // 5. 删除该用户的公告阅读状态
            announcementReadStatusMapper.deleteByUserId(userId);
            
            // 6. 删除该用户的活动记录
            userActivityMapper.deleteByUserId(userId);
            
            // 7. 最后删除用户自身记录
            userMapper.deleteUser(userId);
            
        } catch (Exception e) {
            throw new RuntimeException("用户删除失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 删除用户相关的聊天消息
     * 包括该用户发送的消息和接收的消息
     *
     * @param userId 用户ID
     */
    private void deleteChatMessagesByUserId(Integer userId) {
        // 删除该用户发送的消息
        chatMessageMapper.deleteBySenderId(userId);
        // 删除该用户接收的消息
        chatMessageMapper.deleteByReceiverId(userId);
    }
    
    /**
     * 删除其他用户对该用户的常用联系人关系
     * 需要删除frequent_contact表中contact_id等于该用户ID的记录
     *
     * @param contactId 被删除用户的ID
     */
    private void deleteFrequentContactsByContactId(Integer contactId) {
        frequentContactMapper.deleteByContactId(contactId);
    }

    @Override
    public List<User> findByDepartmentId(Integer departmentId) {
        List<User> users = userMapper.findByDepartmentId(departmentId);
        
        // 为每个用户添加部门信息和最后登录时间
        for (User user : users) {
            // 添加部门信息
            if (user.getDepartmentId() != null) {
                Department department = departmentMapper.findById(user.getDepartmentId());
                if (department != null) {
                    user.setDepartment(department.getDepartmentName());
                }
            }
            
            // 添加最后登录时间
            UserActivity latestActivity = userActivityService.findLatestByUserId(user.getId());
            if (latestActivity != null) {
                user.setLastLogin(latestActivity.getLoginTime());
            }
        }
        
        return users;
    }

    @Override
    public List<User> findAll() {
        List<User> users = userMapper.findAll();
        
        // 为每个用户添加部门信息和最后登录时间
        for (User user : users) {
            // 添加部门信息
            if (user.getDepartmentId() != null) {
                Department department = departmentMapper.findById(user.getDepartmentId());
                if (department != null) {
                    user.setDepartment(department.getDepartmentName());
                }
            }
            
            // 添加最后登录时间
            UserActivity latestActivity = userActivityService.findLatestByUserId(user.getId());
            if (latestActivity != null) {
                user.setLastLogin(latestActivity.getLoginTime());
            }
        }
        
        return users;
    }

    /**
     * 基于关键词搜索用户
     * 说明：调用 mapper 的 searchByKeyword 方法，并为结果补充部门名称与最近登录时间。
     * 
     * @param keyword 关键词
     * @param limit 返回上限
     * @return 用户列表
     */
    @Override
    public List<User> searchUsers(String keyword, Integer limit) {
        if (keyword == null || keyword.trim().length() < 2) {
            throw new RuntimeException("搜索关键词至少需要2个字符");
        }
        if (limit == null || limit <= 0 || limit > 50) {
            limit = 20;
        }
        List<User> users = userMapper.searchByKeyword(keyword.trim(), limit);
        for (User user : users) {
            if (user.getDepartmentId() != null) {
                Department department = departmentMapper.findById(user.getDepartmentId());
                if (department != null) {
                    user.setDepartment(department.getDepartmentName());
                }
            }
            UserActivity latestActivity = userActivityService.findLatestByUserId(user.getId());
            if (latestActivity != null) {
                user.setLastLogin(latestActivity.getLoginTime());
            }
        }
        return users;
    }
}
