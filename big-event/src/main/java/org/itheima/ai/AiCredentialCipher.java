package org.itheima.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import static org.itheima.ai.AiChatController.AiRequestException;

@Component
public class AiCredentialCipher {
    private final byte[] key;
    public AiCredentialCipher(@Value("${ai.credentials.master-key:}") String encoded) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { decoded = new byte[0]; }
        key = decoded.length == 32 ? decoded : null;
    }
    public boolean ready() { return key != null; }
    private void requireReady() {
        if (!ready()) throw new AiRequestException(503, "管理员尚未配置有效的 AI_CREDENTIAL_MASTER_KEY（32 字节 Base64）");
    }
    public String encrypt(int userId, String plaintext) {
        requireReady();
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, userId, nonce);
            return "v1." + Base64.getEncoder().encodeToString(nonce) + "."
                    + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new AiRequestException(503, "凭据加密失败"); }
    }
    public String decrypt(int userId, String encrypted) {
        requireReady();
        try {
            String[] parts = encrypted.split("\\.");
            if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            if (nonce.length != 12) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, userId, nonce)
                    .doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new AiRequestException(503, "凭据无法解密，请联系管理员或重新保存 Key"); }
    }
    private Cipher cipher(int mode, int userId, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("deepseek:v1:user:" + userId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
