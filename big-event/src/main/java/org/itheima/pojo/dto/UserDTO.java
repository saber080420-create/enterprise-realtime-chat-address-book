package org.itheima.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户数据传输对象
 * 用于用户信息的展示，包含部门名称等额外信息
 */
@Data
public class UserDTO {
    private Integer id; // 主键ID
    
    private String username; // 用户名
    
    private String nickname; // 昵称
    
    private String realname; // 真实姓名
    
    private String email; // 邮箱
    
    private String userPic; // 用户头像地址
    
    private Integer departmentId; // 部门ID
    
    private String departmentName; // 部门名称
    
    private String position; // 职位
    
    private String employeeNumber; // 员工编号
    
    private String phone; // 联系电话
    
    private String role; // 角色：system_admin-系统管理员，department_admin-部门管理员，employee-普通员工
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
    
    private Boolean online; // 是否在线
    
    private LocalDateTime lastActiveTime; // 最后活跃时间
}