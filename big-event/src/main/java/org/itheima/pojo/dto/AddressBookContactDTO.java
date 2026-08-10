package org.itheima.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通讯录联系人数据传输对象
 * 用于通讯录联系人信息的传输和展示，包含联系人基础信息、分组信息和状态信息
 */
@Data
public class AddressBookContactDTO {
    // 通讯录记录信息
    private Integer id; // 通讯录记录ID
    private Integer userId; // 通讯录所属用户ID
    private Integer contactId; // 联系人用户ID
    private Integer externalId; // 外部联系人ID（仅外部联系人有值）
    private String contactType; // 联系人类型：internal-内部，external-外部
    private Integer groupId; // 所属分组ID
    private String alias; // 备注名称
    private String tags; // 标签（JSON数组字符串）
    private String remark; // 备注信息
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
    
    // 联系人基础信息
    private String contactName; // 联系人姓名
    private String contactAvatar; // 联系人头像
    private String position; // 联系人职位
    private String departmentName; // 联系人部门名称
    private String phone; // 联系人电话
    private String email; // 联系人邮箱
    
    // 分组信息
    private String groupName; // 分组名称
    
    // 状态信息
    private Boolean online; // 是否在线
    private Long unreadCount; // 未读消息数量
}