package org.itheima.service;

import org.itheima.pojo.GroupAnnouncement;

import java.util.List;
import java.util.Map;

public interface GroupAnnouncementService {
    GroupAnnouncement publish(Integer groupId, Integer publisherId, String title, String content);
    List<Map<String, Object>> listLatest(Integer groupId, Integer limit);
    boolean withdraw(Integer announcementId, Integer operatorId);
    boolean markRead(Integer announcementId, Integer userId);
    List<Map<String, Object>> listReaders(Integer announcementId);
    Long countUnread(Integer groupId, Integer userId);
}


