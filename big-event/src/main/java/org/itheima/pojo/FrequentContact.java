package org.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常用联系人实体类
 */
@Data
public class FrequentContact {
    private Integer id; // 主键ID
    
    private Integer userId; // 用户ID
    
    private Integer contactId; // 联系人ID
    
    private Boolean isFavorite; // 是否收藏
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
}