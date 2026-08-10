package org.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人通讯录实体类
 * 描述：用户将其他用户添加到个人通讯录，并可归属到某个分组。
 */
@Data
public class AddressBook {
    private Integer id; // 记录ID

    private Integer userId; // 通讯录所属用户ID

    private Integer contactId; // 联系人用户ID

    private Integer groupId; // 所属分组ID

    private String alias; // 备注名称

    private String tags; // 标签（JSON数组字符串）

    private String remark; // 备注

    /**
     * 联系人类型：internal-内部员工；external-外部联系人
     * 为兼容历史数据，可为空表示内部
     */
    private String contactType; 

    /**
     * 外部联系人ID（指向external_contact表主键）
     * 当contactType为external时有效；内部联系人该字段为null
     */
    private Integer externalId;

    private LocalDateTime createTime; // 创建时间

    private LocalDateTime updateTime; // 更新时间
}