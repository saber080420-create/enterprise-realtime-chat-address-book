package org.itheima.controller;

import org.itheima.pojo.Result;
import org.itheima.pojo.SystemNotice;
import org.itheima.service.SystemNoticeService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/systemNotice")
public class SystemNoticeController {

    @Autowired
    private SystemNoticeService systemNoticeService;

    @GetMapping("/list")
    public Result<List<SystemNotice>> list(@RequestParam(defaultValue = "20") Integer limit,
                                           @RequestParam(defaultValue = "0") Integer offset) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        List<SystemNotice> list = systemNoticeService.listByUser(userId, limit, offset);
        return Result.success(list);
    }

    @GetMapping("/unreadCount")
    public Result<Integer> unreadCount() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        Integer count = systemNoticeService.countUnread(userId);
        return Result.success(count);
    }

    @PostMapping("/markRead/{id}")
    public Result<Boolean> markRead(@PathVariable Integer id) {
        systemNoticeService.markRead(id);
        return Result.success(true);
    }

    @PostMapping("/markAllRead")
    public Result<Boolean> markAllRead() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        systemNoticeService.markAllRead(userId);
        return Result.success(true);
    }

    /**
     * 删除系统通知（仅已读可删）
     */
    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Integer id) {
        boolean ok = systemNoticeService.deleteIfRead(id);
        if (!ok) return Result.error("仅支持删除已读通知");
        return Result.success(true);
    }
}


