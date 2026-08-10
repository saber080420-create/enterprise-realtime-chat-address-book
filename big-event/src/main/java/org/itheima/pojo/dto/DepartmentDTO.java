package org.itheima.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门数据传输对象
 * 用于部门树形结构的展示
 */
@Data
public class DepartmentDTO {
    private Integer id; // 部门ID
    
    private String departmentName; // 部门名称
    
    private Integer parentId; // 上级部门ID
    
    private String description; // 部门描述
    
    private Integer createUserId; // 创建人ID
    
    private String createUserName; // 创建人姓名
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
    
    private List<DepartmentDTO> children; // 子部门列表
    
    private Integer employeeCount; // 部门员工数量
}