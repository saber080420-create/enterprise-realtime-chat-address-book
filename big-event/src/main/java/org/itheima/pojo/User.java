package org.itheima.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类（扩展为员工）
 */
@Data
public class User {
    private Integer id; // 主键ID
    
    private String username; // 用户名
    
    @JsonIgnore // 把当前对象转换成json字符串的时候，忽略password，最终的json字符串中就没有password的属性
    private String password; // 密码
    
    @NotEmpty
    @Pattern(regexp = "^\\S{1,10}$")
    private String nickname; // 昵称
    
    @NotEmpty
    @Pattern(regexp = "^\\S{1,20}$")
    private String realname; // 真实姓名
    
    @NotEmpty
    @Email
    private String email; // 邮箱
    
    private String userPic; // 用户头像地址
    
    private Integer departmentId; // 部门ID
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String department; // 部门名称（非数据库字段，仅用于传输）
    
    private String position; // 职位
    
    private String employeeNumber; // 员工编号
    
    private String phone; // 联系电话
    
    private String role; // 角色：system_admin-系统管理员，department_admin-部门管理员，employee-普通员工
    
    private String status; // 状态：active-正常，inactive-禁用
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime lastLogin; // 最后登录时间（非数据库字段，仅用于传输）
}
