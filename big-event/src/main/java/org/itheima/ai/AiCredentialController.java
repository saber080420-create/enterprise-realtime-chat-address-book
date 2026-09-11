package org.itheima.ai;

import org.itheima.pojo.Result;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.Semaphore;
import static org.itheima.ai.AiChatController.AiRequestException;

@RestController
@RequestMapping("/ai/config")
public class AiCredentialController {
    private final AiCredentialService service;
    private final Semaphore tests = new Semaphore(2);
    public AiCredentialController(AiCredentialService service) { this.service = service; }
    // Deliberately not a record: auto-generated toString must not disclose the secret.
    public static class KeyInput {
        private String apiKey;
        public void setApiKey(String value) { apiKey = value; }
        public String getApiKey() { return apiKey; }
        @Override public String toString() { return "KeyInput[REDACTED]"; }
    }
    private int user() {
        Map<String,Object> claims = ThreadLocalUtil.get();
        if (claims == null || claims.get("id") == null) throw new AiRequestException(401, "请先登录");
        return Integer.parseInt(claims.get("id").toString());
    }
    @GetMapping public ResponseEntity<?> status() { return ok(service.status(user())); }
    @PutMapping public ResponseEntity<?> save(@RequestBody KeyInput input) {
        service.save(user(), input.getApiKey()); return ok(null);
    }
    @DeleteMapping public ResponseEntity<?> remove() { service.remove(user()); return ok(null); }
    @PostMapping("/test") public ResponseEntity<?> test(@RequestBody KeyInput input) {
        int id = user();
        if (!tests.tryAcquire()) throw new AiRequestException(429, "连接验证繁忙，请稍后重试");
        try { service.test(id, input.getApiKey()); return ok(Map.of("message", "凭据可访问模型列表；实际生成仍取决于余额及模型权限")); }
        finally { tests.release(); }
    }
    private ResponseEntity<?> ok(Object body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Result.success(body));
    }
    @ExceptionHandler(AiRequestException.class) public ResponseEntity<?> failure(AiRequestException e) {
        return ResponseEntity.status(e.status).header("Cache-Control", "no-store").body(Result.error(e.getMessage()));
    }
}
