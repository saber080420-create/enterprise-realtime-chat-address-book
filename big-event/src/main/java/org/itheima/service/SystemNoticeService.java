package org.itheima.service;

import org.itheima.pojo.SystemNotice;
import java.util.List;

public interface SystemNoticeService {
    void sendNotice(Integer userId, Integer operatorId, String type, String title, String content);
    List<SystemNotice> listByUser(Integer userId, Integer limit, Integer offset);
    Integer countUnread(Integer userId);
    void markRead(Integer id);
    void markAllRead(Integer userId);
    boolean deleteIfRead(Integer id);
}


