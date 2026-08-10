package org.itheima.service;

import org.itheima.pojo.User;

import java.util.List;

public interface UserService {
//根据ID查询用户
    User findById(Integer id);
//根据用户名查询用户
    User findByUserName(String username);
//注册
    void register(String username, String password, Integer departmentId);
//更新用户基本信息
    void update(User user);
//更新头像
    void updateAvatar(String avatarUrl);
//更新密码
    void updatePwd(String newPwd);
//更新真实姓名
    void updateRealname(String realname);
//更新昵称
    void updateNickname(String nickname);
//更新邮箱
    void updateEmail(String email);
//更新电话
    void updatePhone(String phone);
//更新用户角色
    void updateRole(Integer userId, String role);
//更新用户状态（启用/禁用）
    void updateStatus(Integer userId, String status);
//更新用户部门
    void updateDepartment(Integer userId, Integer departmentId);
//更新用户职位
    void updatePosition(Integer userId, String position);
//删除用户
    void deleteUser(Integer userId);
//根据部门ID查询用户列表
    List<User> findByDepartmentId(Integer departmentId);
//查询所有用户
    List<User> findAll();

    /**
     * 基于关键词搜索用户（仅用于选择外部联系人，按小写模糊匹配多个字段）
     * 注意：非系统管理员仅返回同部门外的可见最小信息；控制器层会做权限校验。
     *
     * @param keyword 关键词（至少2个字符）
     * @param limit 返回上限（默认10-20之间）
     * @return 用户列表
     */
    List<User> searchUsers(String keyword, Integer limit);
}
