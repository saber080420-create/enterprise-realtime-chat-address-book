package org.itheima.controller;

import org.itheima.pojo.Result;
import org.itheima.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/upload")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    /**
     * 上传头像文件
     * 
     * @param file 上传的文件
     * @return 包含文件URL的结果
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            // 调用服务层处理文件上传
            String fileUrl = fileUploadService.uploadAvatar(file);
            return Result.success(fileUrl);
        } catch (IllegalArgumentException e) {
            // 参数错误（文件为空、格式不对、大小超限等）
            return Result.error(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 聊天：上传图片附件
     * 函数级注释：
     * - 校验图片类型与大小（≤5MB，白名单：jpg/jpeg/png/gif/webp）。
     * - 保存至 uploads/chat/images，返回元数据（url、name、size、mime、width、height）。
     */
    @PostMapping("/chat/image")
    public Result<Map<String, Object>> uploadChatImage(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> meta = fileUploadService.uploadChatImage(file);
            return Result.success(meta);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 聊天：上传通用文件附件
     * 函数级注释：
     * - 校验类型与大小（≤20MB，白名单：pdf/zip/doc/docx/xls/xlsx/txt）。
     * - 保存至 uploads/chat/files，返回元数据（url、name、size、mime）。
     */
    @PostMapping("/chat/file")
    public Result<Map<String, Object>> uploadChatFile(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> meta = fileUploadService.uploadChatFile(file);
            return Result.success(meta);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }
}