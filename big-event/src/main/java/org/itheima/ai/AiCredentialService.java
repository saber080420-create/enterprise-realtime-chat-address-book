package org.itheima.ai;

import org.springframework.stereotype.Service;
import java.util.Map;
import static org.itheima.ai.AiChatController.AiRequestException;

@Service
public class AiCredentialService {
    private final AiCredentialMapper repository;
    private final AiCredentialCipher cipher;
    private final DeepSeekGateway template;
    public AiCredentialService(AiCredentialMapper repository, AiCredentialCipher cipher, DeepSeekGateway template) {
        this.repository = repository; this.cipher = cipher; this.template = template;
    }
    private void authorize(int userId) {
        if (!java.util.Objects.equals(repository.activeUser(userId), userId)) throw new AiRequestException(403, "账号不可用");
    }
    public Map<String, Object> status(int userId) {
        authorize(userId);
        String encrypted = repository.read(userId);
        String suffix = "";
        boolean usable = false;
        if (encrypted != null && cipher.ready()) {
            try {
                String key = cipher.decrypt(userId, encrypted);
                suffix = key.substring(Math.max(0, key.length() - 4)); usable = true;
            } catch (AiRequestException ignored) { /* Never return ciphertext or decryption details. */ }
        }
        return Map.of("configured", usable, "saved", encrypted != null, "storageReady", cipher.ready(),
                "keySuffix", suffix, "provider", "DeepSeek", "model", template.model());
    }
    static String validate(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,256}"))
            throw new AiRequestException(400, "Key 应为 16–256 位字母、数字、下划线或连字符，请勿包含空白");
        return key;
    }
    public void save(int userId, String key) {
        authorize(userId); repository.save(userId, cipher.encrypt(userId, validate(key)));
    }
    public void remove(int userId) { authorize(userId); repository.remove(userId); }
    public DeepSeekGateway forUser(int userId) {
        authorize(userId);
        String encrypted = repository.read(userId);
        if (encrypted == null) throw new AiRequestException(503, "请先在模型配置中保存自己的 DeepSeek API Key");
        return template.withUserKey(cipher.decrypt(userId, encrypted));
    }
    public void test(int userId, String key) {
        authorize(userId);
        try { template.withUserKey(validate(key)).testConnection(); }
        catch (java.io.IOException e) { throw new AiRequestException(502, "连接验证失败：请检查 Key、网络或 DeepSeek 服务状态"); }
    }
}
