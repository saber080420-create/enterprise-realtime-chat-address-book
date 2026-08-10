package org.itheima.pojo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群公告实体
 */
@Data
public class GroupAnnouncement {
    private Integer id;

    @NotNull
    private Integer groupId;

    @NotNull
    private Integer publisherId; // 一般为群主

    @NotEmpty
    private String title;

    @NotEmpty
    private String content;

    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}


