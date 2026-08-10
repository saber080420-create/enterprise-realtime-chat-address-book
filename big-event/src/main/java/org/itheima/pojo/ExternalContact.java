package org.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部联系人实体类
 * 用于存储企业外部联系人的基础信息，如客户、合作伙伴等
 */
@Data
public class ExternalContact {
    /**
     * 外部联系人ID
     */
    private Integer id;

    /**
     * 联系人姓名
     */
    private String name;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 公司名称或部门
     */
    private String company;

    /**
     * 职位
     */
    private String position;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}