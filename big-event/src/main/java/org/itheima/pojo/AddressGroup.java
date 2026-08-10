package org.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通讯录分组实体类
 * 描述：每个用户可以拥有多个分组，用于给个人通讯录里的联系人进行分类。
 */
@Data
public class AddressGroup {
    private Integer id; // 分组ID

    private Integer userId; // 分组所属用户ID

    private String groupName; // 分组名称

    private Boolean isDefault; // 是否默认分组：true-默认分组

    private Integer sortOrder; // 排序序号

    private LocalDateTime createTime; // 创建时间

    private LocalDateTime updateTime; // 更新时间
}