package org.itheima.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户在线状态数据传输对象
 * 用于传递用户在线状态信息
 */
@Data
public class UserStatusDTO {
    private Integer userId; // 用户ID
    
    private String username; // 用户名
    
    private String nickname; // 用户昵称
    
    private String userPic; // 用户头像
    
    private Boolean online; // 是否在线
    
    private LocalDateTime lastActiveTime; // 最后活跃时间
    
    /**
     * 创建用户在线状态DTO
     */
    public static UserStatusDTO of(Integer userId, String username, String nickname, String userPic, Boolean online, LocalDateTime lastActiveTime) {
        UserStatusDTO dto = new UserStatusDTO();
        dto.setUserId(userId);
        dto.setUsername(username);
        dto.setNickname(nickname);
        dto.setUserPic(userPic);
        dto.setOnline(online);
        dto.setLastActiveTime(lastActiveTime);
        return dto;
    }
}