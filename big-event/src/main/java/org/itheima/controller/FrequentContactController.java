package org.itheima.controller;

import org.itheima.pojo.FrequentContact;
import org.itheima.pojo.Result;
import org.itheima.pojo.User;
import org.itheima.service.FrequentContactService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 常用联系人控制器
 */
@RestController
@RequestMapping("/contact/frequent")
@Validated
public class FrequentContactController {

    @Autowired
    private FrequentContactService frequentContactService;

    /**
     * 获取当前用户的常用联系人列表
     * 
     * @param limit 限制数量
     * @return 常用联系人列表
     */
    @GetMapping
    public Result<List<User>> getFrequentContacts(
            @RequestParam(defaultValue = "10") Integer limit) {
        // 获取当前登录用户ID
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        
        List<User> contacts = frequentContactService.findMostFrequentWithUserInfoByUserId(userId, limit);
        return Result.success(contacts);
    }

    /**
     * 获取当前用户的收藏联系人列表
     * 
     * @return 收藏联系人列表
     */
    @GetMapping("/favorites")
    public Result<List<User>> getFavoriteContacts() {
        // 获取当前登录用户ID
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        
        List<User> contacts = frequentContactService.findFavoritesWithUserInfoByUserId(userId);
        return Result.success(contacts);
    }

    /**
     * 获取当前用户的最近联系人列表
     * 
     * @param limit 限制数量
     * @return 最近联系人列表
     */
    @GetMapping("/most-frequent")
    public Result<List<User>> getMostFrequentContacts(
            @RequestParam(defaultValue = "5") Integer limit) {
        // 获取当前登录用户ID
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        
        List<User> contacts = frequentContactService.findMostFrequentWithUserInfoByUserId(userId, limit);
        return Result.success(contacts);
    }

    /**
     * 设置收藏状态
     * 
     * @param contactId 联系人ID
     * @param isFavorite 是否收藏
     * @return 更新结果
     */
    @PutMapping("/favorite/{contactId}")
    public Result<FrequentContact> setFavoriteStatus(
            @PathVariable Integer contactId,
            @RequestParam Boolean isFavorite) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            FrequentContact contact = frequentContactService.setFavoriteStatus(userId, contactId, isFavorite);
            return Result.success(contact);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除常用联系人
     * 
     * @param contactId 联系人ID
     * @return 删除结果
     */
    @DeleteMapping("/{contactId}")
    public Result<Boolean> deleteFrequentContact(@PathVariable Integer contactId) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            boolean result = frequentContactService.deleteByUserIdAndContactId(userId, contactId);
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取联系人详细信息
     * 
     * @param contactId 联系人ID
     * @return 联系人详细信息
     */
    @GetMapping("/{contactId}")
    public Result<Map<String, Object>> getContactDetail(@PathVariable Integer contactId) {
        try {
            // 获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");
            
            Map<String, Object> detail = frequentContactService.getContactDetail(userId, contactId);
            return Result.success(detail);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 添加常用联系人
     * 提供当前登录用户将某个用户添加为常用联系人的能力
     * 对应前端场景：在通讯录中通过搜索结果将某人加入常用联系人
     *
     * @param contactId 要添加的联系人用户ID
     * @param isFavorite 是否直接设为收藏（可选，默认 false）
     * @return 新增的常用联系人关系对象
     */
    @PostMapping("/{contactId}")
    public Result<FrequentContact> addFrequentContact(@PathVariable Integer contactId,
                                                      @RequestParam(name = "isFavorite", required = false, defaultValue = "false") Boolean isFavorite) {
        try {
            // 从 ThreadLocal 中获取当前登录用户ID
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer userId = (Integer) claims.get("id");

            // 业务兜底：不可将自己添加为自己的常用联系人
            if (userId != null && userId.equals(contactId)) {
                return Result.error("不能将自己添加为常用联系人");
            }

            // 组装关系对象并调用服务层新增
            FrequentContact fc = new FrequentContact();
            fc.setUserId(userId);
            fc.setContactId(contactId);
            fc.setIsFavorite(isFavorite);

            FrequentContact created = frequentContactService.add(fc);
            return Result.success(created);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}