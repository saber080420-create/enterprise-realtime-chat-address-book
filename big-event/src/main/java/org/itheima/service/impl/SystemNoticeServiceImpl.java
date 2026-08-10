package org.itheima.service.impl;

import org.itheima.mapper.SystemNoticeMapper;
import org.itheima.pojo.SystemNotice;
import org.itheima.service.SystemNoticeService;
import org.itheima.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemNoticeServiceImpl implements SystemNoticeService {

    @Autowired
    private SystemNoticeMapper systemNoticeMapper;

    @Autowired
    private WebSocketService webSocketService;

    @Override
    public void sendNotice(Integer userId, Integer operatorId, String type, String title, String content) {
        SystemNotice notice = new SystemNotice();
        notice.setUserId(userId);
        notice.setOperatorId(operatorId);
        notice.setType(type);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setIsRead(false);
        systemNoticeMapper.add(notice);
        try {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("event", "system_notice");
            data.put("title", title);
            data.put("content", content);
            data.put("type", type);
            data.put("time", System.currentTimeMillis());
            webSocketService.sendNotificationToUser(userId, data);
        } catch (Exception ignored) {}
    }

    @Override
    public List<SystemNotice> listByUser(Integer userId, Integer limit, Integer offset) {
        return systemNoticeMapper.findByUserId(userId, limit, offset);
    }

    @Override
    public Integer countUnread(Integer userId) {
        return systemNoticeMapper.countUnread(userId);
    }

    @Override
    public void markRead(Integer id) {
        systemNoticeMapper.markRead(id);
    }

    @Override
    public void markAllRead(Integer userId) {
        systemNoticeMapper.markAllRead(userId);
    }

    @Override
    public boolean deleteIfRead(Integer id) {
        return systemNoticeMapper.deleteIfRead(id) > 0;
    }
}


