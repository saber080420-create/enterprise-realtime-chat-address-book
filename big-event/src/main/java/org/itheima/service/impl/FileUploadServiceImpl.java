package org.itheima.service.impl;

import org.itheima.service.FileUploadService;
import org.itheima.service.UserService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/**
 * 文件上传服务实现类
 */
@Service
public class FileUploadServiceImpl implements FileUploadService {

    @Value("${file.upload.path:uploads}")
    private String uploadPath;

    @Value("${file.upload.url:http://localhost:8081}")
    private String serverUrl;

    @Autowired
    private UserService userService;

    @Override
    public String uploadAvatar(MultipartFile file) throws Exception {
        // 检查文件是否为空
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只能上传图片文件");
        }

        // 检查文件大小（限制为2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过2MB");
        }

        // 获取当前用户ID
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");

        // 创建上传目录
        String avatarDir = uploadPath + "/avatars";
        Path uploadDir = Paths.get(avatarDir);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        // 生成文件名
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null) {
            extension = "jpg";
        }
        String filename = "user_" + userId + "_" + UUID.randomUUID() + "." + extension;

        // 保存文件
        Path filePath = uploadDir.resolve(filename);
        try {
            Files.copy(file.getInputStream(), filePath);
        } catch (IOException e) {
            throw new IOException("保存文件失败: " + e.getMessage(), e);
        }

        // 构建相对访问路径（交由 Vite 5173 代理到后端）
        String relativePath = "/uploads/avatars/" + filename;

        // 更新用户头像路径（存相对路径，避免端口耦合）
        userService.updateAvatar(relativePath);

        // 返回相对路径给前端
        return relativePath;
    }

    /**
     * 聊天：上传图片附件
     * 函数级注释：
     * - 校验图片类型与大小（≤5MB，白名单：jpg/jpeg/png/gif/webp）。
     * - 保存到 uploads/chat/images 目录，生成唯一文件名。
     * - 返回附件元数据（url、name、size、mime、width、height）。
     */
    @Override
    public Map<String, Object> uploadChatImage(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null) extension = "";
        String extLower = extension.toLowerCase();

        // 白名单校验
        boolean mimeOk = contentType != null && contentType.startsWith("image/");
        boolean extOk = extLower.equals("jpg") || extLower.equals("jpeg") || extLower.equals("png") || extLower.equals("gif") || extLower.equals("webp");
        if (!mimeOk || !extOk) {
            throw new IllegalArgumentException("不支持的图片类型，只允许 jpg/jpeg/png/gif/webp");
        }
        // 大小限制：5MB
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("图片大小不能超过5MB");
        }

        // 目录：uploads/chat/images
        String imageDir = uploadPath + "/chat/images";
        Path uploadDir = Paths.get(imageDir);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        // 文件名：user_{id}_{uuid}.ext
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        String filename = "user_" + userId + "_" + UUID.randomUUID() + (StringUtils.hasText(extension) ? "." + extLower : "");

        Path filePath = uploadDir.resolve(filename);
        try {
            Files.copy(file.getInputStream(), filePath);
        } catch (IOException e) {
            throw new IOException("保存文件失败: " + e.getMessage(), e);
        }

        // 读取图片尺寸
        Integer width = null;
        Integer height = null;
        try {
            BufferedImage img = ImageIO.read(filePath.toFile());
            if (img != null) {
                width = img.getWidth();
                height = img.getHeight();
            }
        } catch (Exception ignore) {
        }

        String relativeUrl = "/uploads/chat/images/" + filename;
        Map<String, Object> meta = new HashMap<>();
        meta.put("url", relativeUrl);
        meta.put("name", originalFilename);
        meta.put("size", file.getSize());
        meta.put("mime", contentType);
        if (width != null && height != null) {
            meta.put("width", width);
            meta.put("height", height);
        }
        return meta;
    }

    /**
     * 聊天：上传通用文件附件
     * 函数级注释：
     * - 校验文件类型与大小（≤20MB，白名单：pdf/zip/doc/docx/xls/xlsx/txt）。
     * - 保存到 uploads/chat/files 目录，生成唯一文件名。
     * - 返回附件元数据（url、name、size、mime）。
     */
    @Override
    public Map<String, Object> uploadChatFile(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null) extension = "";
        String extLower = extension.toLowerCase();

        // 扩展名白名单
        boolean extOk = extLower.equals("pdf") || extLower.equals("zip") || extLower.equals("doc") || extLower.equals("docx") || extLower.equals("xls") || extLower.equals("xlsx") || extLower.equals("txt");
        if (!extOk) {
            throw new IllegalArgumentException("不支持的文件类型，只允许 pdf/zip/doc/docx/xls/xlsx/txt");
        }
        // 大小限制：20MB
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过20MB");
        }

        // 目录：uploads/chat/files
        String fileDir = uploadPath + "/chat/files";
        Path uploadDir = Paths.get(fileDir);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        // 文件名：user_{id}_{uuid}.ext
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        String filename = "user_" + userId + "_" + UUID.randomUUID() + (StringUtils.hasText(extension) ? "." + extLower : "");

        Path filePath = uploadDir.resolve(filename);
        try {
            Files.copy(file.getInputStream(), filePath);
        } catch (IOException e) {
            throw new IOException("保存文件失败: " + e.getMessage(), e);
        }

        String relativeUrl = "/uploads/chat/files/" + filename;
        Map<String, Object> meta = new HashMap<>();
        meta.put("url", relativeUrl);
        meta.put("name", originalFilename);
        meta.put("size", file.getSize());
        meta.put("mime", contentType);
        return meta;
    }
}