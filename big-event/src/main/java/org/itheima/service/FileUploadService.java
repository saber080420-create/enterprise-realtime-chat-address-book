package org.itheima.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

/**
 * 文件上传服务接口
 */
public interface FileUploadService {
    
    /**
     * 上传头像文件
     *
     * @param file 上传的文件
     * @return 文件访问URL
     * @throws Exception 上传过程中的异常
     */
    String uploadAvatar(MultipartFile file) throws Exception;

    /**
     * 聊天：上传图片附件
     * 函数级注释：
     * - 校验图片类型与大小（≤5MB，白名单：jpg/jpeg/png/gif/webp）。
     * - 保存到 uploads/chat/images 目录，生成唯一文件名。
     * - 返回附件元数据（url、name、size、mime、width、height）。
     */
    Map<String, Object> uploadChatImage(MultipartFile file) throws Exception;

    /**
     * 聊天：上传通用文件附件
     * 函数级注释：
     * - 校验文件类型与大小（≤20MB，白名单：pdf/zip/doc/docx/xls/xlsx/txt）。
     * - 保存到 uploads/chat/files 目录，生成唯一文件名。
     * - 返回附件元数据（url、name、size、mime）。
     */
    Map<String, Object> uploadChatFile(MultipartFile file) throws Exception;
}