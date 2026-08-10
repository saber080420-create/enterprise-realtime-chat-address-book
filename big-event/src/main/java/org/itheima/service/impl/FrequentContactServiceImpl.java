package org.itheima.service.impl;

import org.itheima.mapper.FrequentContactMapper;
import org.itheima.mapper.UserMapper;
import org.itheima.pojo.FrequentContact;
import org.itheima.pojo.User;
import org.itheima.service.FrequentContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 常用联系人服务实现类
 */
@Service
public class FrequentContactServiceImpl implements FrequentContactService {
    
    @Autowired
    private FrequentContactMapper frequentContactMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Override
    public FrequentContact findById(Integer id) {
        return frequentContactMapper.findById(id);
    }
    
    @Override
    public List<FrequentContact> findByUserId(Integer userId) {
        return frequentContactMapper.findByUserId(userId);
    }
    
    @Override
    public List<User> findContactsWithUserInfoByUserId(Integer userId) {
        return frequentContactMapper.findContactsWithUserInfoByUserId(userId);
    }
    
    @Override
    public List<FrequentContact> findFavoritesByUserId(Integer userId) {
        return frequentContactMapper.findFavoritesByUserId(userId);
    }
    
    @Override
    public List<User> findFavoritesWithUserInfoByUserId(Integer userId) {
        return frequentContactMapper.findFavoritesWithUserInfoByUserId(userId);
    }
    
    @Override
    public List<FrequentContact> findMostFrequentByUserId(Integer userId, Integer limit) {
        return frequentContactMapper.findMostFrequentByUserId(userId, limit);
    }
    
    @Override
    public List<User> findMostFrequentWithUserInfoByUserId(Integer userId, Integer limit) {
        return frequentContactMapper.findMostFrequentWithUserInfoByUserId(userId, limit);
    }
    
    @Override
    public FrequentContact findByUserIdAndContactId(Integer userId, Integer contactId) {
        return frequentContactMapper.findByUserIdAndContactId(userId, contactId);
    }
    
    @Override
    @Transactional
    public FrequentContact add(FrequentContact frequentContact) {
        // 检查用户是否存在
        User user = userMapper.findById(frequentContact.getUserId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查联系人是否存在
        User contact = userMapper.findById(frequentContact.getContactId());
        if (contact == null) {
            throw new RuntimeException("联系人不存在");
        }
        
        // 检查是否已经存在该常用联系人
        FrequentContact existingContact = frequentContactMapper.findByUserIdAndContactId(
                frequentContact.getUserId(), frequentContact.getContactId());
        if (existingContact != null) {
            throw new RuntimeException("该联系人已经是常用联系人");
        }
        
        // 设置默认值
        LocalDateTime now = LocalDateTime.now();
        if (frequentContact.getIsFavorite() == null) {
            frequentContact.setIsFavorite(false);
        }
        frequentContact.setCreateTime(now);
        frequentContact.setUpdateTime(now);
        
        // 添加常用联系人
        frequentContactMapper.add(frequentContact);
        
        return frequentContact;
    }
    
    @Override
    @Transactional
    public FrequentContact update(FrequentContact frequentContact) {
        // 检查常用联系人是否存在
        FrequentContact existingContact = frequentContactMapper.findById(frequentContact.getId());
        if (existingContact == null) {
            throw new RuntimeException("常用联系人不存在");
        }
        
        // 更新时间
        frequentContact.setUpdateTime(LocalDateTime.now());
        
        // 更新常用联系人
        frequentContactMapper.update(frequentContact);
        
        return frequentContact;
    }
    
    @Override
    @Transactional
    public boolean deleteById(Integer id) {
        // 检查常用联系人是否存在
        FrequentContact existingContact = frequentContactMapper.findById(id);
        if (existingContact == null) {
            throw new RuntimeException("常用联系人不存在");
        }
        
        // 删除常用联系人
        return frequentContactMapper.deleteById(id) > 0;
    }
    
    @Override
    @Transactional
    public boolean deleteByUserIdAndContactId(Integer userId, Integer contactId) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查联系人是否存在
        User contact = userMapper.findById(contactId);
        if (contact == null) {
            throw new RuntimeException("联系人不存在");
        }
        
        // 删除常用联系人
        return frequentContactMapper.deleteByUserIdAndContactId(userId, contactId) > 0;
    }

    @Override
    @Transactional
    public FrequentContact setFavoriteStatus(Integer userId, Integer contactId, Boolean isFavorite) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查联系人是否存在
        User contact = userMapper.findById(contactId);
        if (contact == null) {
            throw new RuntimeException("联系人不存在");
        }
        
        // 查找常用联系人
        FrequentContact frequentContact = frequentContactMapper.findByUserIdAndContactId(userId, contactId);
        LocalDateTime now = LocalDateTime.now();
        
        if (frequentContact == null) {
            // 如果不存在，则创建新的常用联系人
            frequentContact = new FrequentContact();
            frequentContact.setUserId(userId);
            frequentContact.setContactId(contactId);
            frequentContact.setIsFavorite(isFavorite);
            frequentContact.setCreateTime(now);
            frequentContact.setUpdateTime(now);
            
            frequentContactMapper.add(frequentContact);
        } else {
            // 如果存在，则更新收藏状态
            frequentContact.setIsFavorite(isFavorite);
            frequentContact.setUpdateTime(now);
            
            frequentContactMapper.update(frequentContact);
        }
        
        return frequentContact;
    }

    @Override
    public boolean isFavoriteContact(Integer userId, Integer contactId) {
        // 查找常用联系人
        FrequentContact frequentContact = frequentContactMapper.findByUserIdAndContactId(userId, contactId);
        
        // 如果不存在或者不是收藏的，则返回false
        if (frequentContact == null || !Boolean.TRUE.equals(frequentContact.getIsFavorite())) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public Map<String, Object> getContactDetail(Integer userId, Integer contactId) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查联系人是否存在
        User contact = userMapper.findById(contactId);
        if (contact == null) {
            throw new RuntimeException("联系人不存在");
        }
        
        // 查找常用联系人关系
        FrequentContact frequentContact = frequentContactMapper.findByUserIdAndContactId(userId, contactId);
        
        // 准备返回结果
        Map<String, Object> detail = new HashMap<>();
        
        // 添加联系人基本信息
        detail.put("id", contact.getId());
        detail.put("username", contact.getUsername());
        detail.put("nickname", contact.getNickname());
        detail.put("email", contact.getEmail());
        detail.put("userPic", contact.getUserPic());
        detail.put("departmentId", contact.getDepartmentId());
        detail.put("position", contact.getPosition());
        detail.put("employeeNumber", contact.getEmployeeNumber());
        detail.put("phone", contact.getPhone());
        detail.put("role", contact.getRole());
        
        // 添加联系关系信息
        if (frequentContact != null) {
            detail.put("isFavorite", frequentContact.getIsFavorite());
            detail.put("createTime", frequentContact.getCreateTime());
            detail.put("updateTime", frequentContact.getUpdateTime());
        } else {
            detail.put("isFavorite", false);
            detail.put("createTime", null);
            detail.put("updateTime", null);
        }
        
        return detail;
    }
}