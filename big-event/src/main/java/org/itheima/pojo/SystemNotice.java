package org.itheima.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SystemNotice {
    private Integer id;
    private Integer userId;
    private Integer operatorId;
    private String type;
    private String title;
    private String content;
    private Boolean isRead;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}


