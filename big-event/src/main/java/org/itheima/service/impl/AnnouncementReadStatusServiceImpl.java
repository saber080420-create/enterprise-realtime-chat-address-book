package org.itheima.service.impl;

import org.itheima.mapper.AnnouncementMapper;
import org.itheima.mapper.AnnouncementReadStatusMapper;
import org.itheima.pojo.Announcement;
import org.itheima.pojo.AnnouncementReadStatus;
import org.itheima.service.AnnouncementReadStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 公告阅读状态服务实现类
 */
@Service
public class AnnouncementReadStatusServiceImpl implements AnnouncementReadStatusService {

    @Autowired
    private AnnouncementReadStatusMapper readStatusMapper;
    
    @Autowired
    private AnnouncementMapper announcementMapper;

    @Override
    public AnnouncementReadStatus findById(Integer id) {
        return readStatusMapper.findById(id);
    }

    @Override
    public List<AnnouncementReadStatus> findByAnnouncementId(Integer announcementId) {
        return readStatusMapper.findByAnnouncementId(announcementId);
    }

    @Override
    public List<AnnouncementReadStatus> findByUserId(Integer userId) {
        return readStatusMapper.findByUserId(userId);
    }

    @Override
    public AnnouncementReadStatus findByAnnouncementIdAndUserId(Integer announcementId, Integer userId) {
        return readStatusMapper.findByAnnouncementIdAndUserId(announcementId, userId);
    }

    @Override
    @Transactional
    public AnnouncementReadStatus add(AnnouncementReadStatus readStatus) {
        // 检查公告是否存在
        Announcement announcement = announcementMapper.findById(readStatus.getAnnouncementId());
        if (announcement == null) {
            throw new RuntimeException("公告不存在");
        }
        
        // 检查是否已存在阅读状态
        AnnouncementReadStatus existingStatus = readStatusMapper.findByAnnouncementIdAndUserId(
                readStatus.getAnnouncementId(), readStatus.getUserId());
        if (existingStatus != null) {
            return existingStatus;
        }
        
        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        readStatus.setCreateTime(now);
        readStatus.setUpdateTime(now);
        
        // 如果是已读状态，设置阅读时间
        if (readStatus.getIsRead()) {
            readStatus.setReadTime(now);
        }
        
        // 保存阅读状态
        readStatusMapper.add(readStatus);
        
        return readStatus;
    }

    @Override
    @Transactional
    public AnnouncementReadStatus update(AnnouncementReadStatus readStatus) {
        // 检查阅读状态是否存在
        AnnouncementReadStatus existingStatus = readStatusMapper.findById(readStatus.getId());
        if (existingStatus == null) {
            throw new RuntimeException("阅读状态不存在");
        }
        
        // 更新阅读状态
        readStatus.setUpdateTime(LocalDateTime.now());
        
        // 如果从未读变为已读，设置阅读时间
        if (!existingStatus.getIsRead() && readStatus.getIsRead()) {
            readStatus.setReadTime(LocalDateTime.now());
        }
        
        readStatusMapper.update(readStatus);
        
        return readStatus;
    }

    @Override
    @Transactional
    public boolean deleteById(Integer id) {
        // 检查阅读状态是否存在
        AnnouncementReadStatus existingStatus = readStatusMapper.findById(id);
        if (existingStatus == null) {
            throw new RuntimeException("阅读状态不存在");
        }
        
        // 删除阅读状态
        readStatusMapper.deleteById(id);
        
        return true;
    }

    @Override
    @Transactional
    public AnnouncementReadStatus markAsRead(Integer announcementId, Integer userId) {
        // 检查公告是否存在
        Announcement announcement = announcementMapper.findById(announcementId);
        if (announcement == null) {
            throw new RuntimeException("公告不存在");
        }
        
        // 检查是否已存在阅读状态
        AnnouncementReadStatus existingStatus = readStatusMapper.findByAnnouncementIdAndUserId(announcementId, userId);
        LocalDateTime now = LocalDateTime.now();
        
        if (existingStatus != null) {
            // 如果已存在且未读，则标记为已读
            if (!existingStatus.getIsRead()) {
                existingStatus.setIsRead(true);
                existingStatus.setReadTime(now);
                existingStatus.setUpdateTime(now);
                readStatusMapper.update(existingStatus);
            }
            return existingStatus;
        } else {
            // 如果不存在，则创建新的阅读状态
            AnnouncementReadStatus readStatus = new AnnouncementReadStatus();
            readStatus.setAnnouncementId(announcementId);
            readStatus.setUserId(userId);
            readStatus.setIsRead(true);
            readStatus.setReadTime(now);
            readStatus.setCreateTime(now);
            readStatus.setUpdateTime(now);
            
            readStatusMapper.add(readStatus);
            
            return readStatus;
        }
    }

    @Override
    @Transactional
    public int markMultipleAsRead(List<Integer> announcementIds, Integer userId) {
        if (announcementIds == null || announcementIds.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        List<AnnouncementReadStatus> statusesToAdd = new ArrayList<>();
        
        for (Integer announcementId : announcementIds) {
            // 检查公告是否存在
            Announcement announcement = announcementMapper.findById(announcementId);
            if (announcement == null) {
                continue;
            }
            
            // 检查是否已存在阅读状态
            AnnouncementReadStatus existingStatus = readStatusMapper.findByAnnouncementIdAndUserId(announcementId, userId);
            
            if (existingStatus != null) {
                // 如果已存在且未读，则标记为已读
                if (!existingStatus.getIsRead()) {
                    existingStatus.setIsRead(true);
                    existingStatus.setReadTime(now);
                    existingStatus.setUpdateTime(now);
                    readStatusMapper.update(existingStatus);
                    count++;
                }
            } else {
                // 如果不存在，则创建新的阅读状态
                AnnouncementReadStatus readStatus = new AnnouncementReadStatus();
                readStatus.setAnnouncementId(announcementId);
                readStatus.setUserId(userId);
                readStatus.setIsRead(true);
                readStatus.setReadTime(now);
                readStatus.setCreateTime(now);
                readStatus.setUpdateTime(now);
                
                statusesToAdd.add(readStatus);
            }
        }
        
        // 批量添加阅读状态
        if (!statusesToAdd.isEmpty()) {
            count += readStatusMapper.batchAdd(statusesToAdd);
        }
        
        return count;
    }

    @Override
    public Integer countReadByAnnouncementId(Integer announcementId) {
        return readStatusMapper.countReadByAnnouncementId(announcementId);
    }

    @Override
    public Integer countUnreadByUserId(Integer userId) {
        return readStatusMapper.countUnreadByUserId(userId);
    }
    
    @Transactional
    public boolean markAsReadBatch(List<Integer> announcementIds, Integer userId) {
        if (announcementIds == null || announcementIds.isEmpty()) {
            return true;
        }
        
        int count = markMultipleAsRead(announcementIds, userId);
        return count > 0 || announcementIds.size() == 0;
    }
    
    @Override
    public List<Integer> findReadAnnouncementIdsByUserId(Integer userId) {
        return readStatusMapper.findReadAnnouncementIdsByUserId(userId);
    }
    
    @Override
    public List<Integer> findUnreadAnnouncementIdsByUserId(Integer userId) {
        return readStatusMapper.findUnreadAnnouncementIdsByUserId(userId);
    }
    
    /**
     * 获取公告已读用户详情列表
     */
    @Override
    public List<java.util.Map<String, Object>> findReadUsersByAnnouncementId(Integer announcementId) {
        return readStatusMapper.findReadUsersByAnnouncementId(announcementId);
    }
}