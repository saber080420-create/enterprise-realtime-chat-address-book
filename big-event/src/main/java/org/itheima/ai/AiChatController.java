package org.itheima.ai;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import org.itheima.pojo.Result;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/ai")
public class AiChatController {
    private final ChatModelGateway gateway;
    private final AnnouncementKnowledgeService knowledge;
    private final AnnouncementVectorService vectors;
    private final DocumentKnowledgeService documents;
    private final DocumentVectorService documentVectors;
    private final GroupSummaryService summaries;
    private final ConcurrentMap<Integer, Generation> activeUsers = new ConcurrentHashMap<>();
    private static class Generation {
        final String id = UUID.randomUUID().toString();
        final ChatModelGateway.Cancellation cancellation = new ChatModelGateway.Cancellation();
        final CompletableFuture<Void> finished = new CompletableFuture<>();
    }
    private final ExecutorService workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    public AiChatController(ChatModelGateway gateway, AnnouncementKnowledgeService knowledge, AnnouncementVectorService vectors,
                            DocumentKnowledgeService documents, DocumentVectorService documentVectors, GroupSummaryService summaries) {
        this.gateway = gateway;
        this.knowledge = knowledge;
        this.vectors = vectors;
        this.documents = documents;
        this.documentVectors = documentVectors;
        this.summaries = summaries;
    }
    AiChatController(ChatModelGateway gateway, AnnouncementKnowledgeService knowledge, AnnouncementVectorService vectors,
                     DocumentKnowledgeService documents, DocumentVectorService documentVectors) { this(gateway, knowledge, vectors, documents, documentVectors, null); }
    AiChatController(ChatModelGateway gateway, AnnouncementKnowledgeService knowledge, AnnouncementVectorService vectors,
                     DocumentKnowledgeService documents) { this(gateway, knowledge, vectors, documents, null); }
    AiChatController(ChatModelGateway gateway, AnnouncementKnowledgeService knowledge, AnnouncementVectorService vectors) { this(gateway, knowledge, vectors, null); }
    AiChatController(ChatModelGateway gateway, AnnouncementKnowledgeService knowledge) { this(gateway, knowledge, null); }
    AiChatController(ChatModelGateway gateway) { this(gateway, null, null); }

    @GetMapping("/status")
    public Result<?> status() {
        return Result.success(Map.of("configured", gateway.configured(), "model", gateway.model(),
                "protocolVersion", 2, "knowledgeMode", "announcements-keyword-v1", "vectorEnabled", vectors != null && vectors.enabled(),
                "documentsEnabled", documents != null, "documentVectorEnabled", documentVectors != null && documentVectors.enabled(),
                "groupSummaryEnabled", summaries != null));
    }

    @PostMapping("/chat")
    public ResponseEntity<SseEmitter> chat(@RequestBody AiChatRequest request, HttpServletResponse response) {
        try { request.validate(); }
        catch (IllegalArgumentException e) { throw new AiRequestException(400, e.getMessage()); }
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null || claims.get("id") == null) throw new AiRequestException(401, "请先登录");
        if (!gateway.configured()) throw new AiRequestException(503, "AI 尚未配置，请在后端设置 DEEPSEEK_API_KEY");
        int userId = Integer.parseInt(claims.get("id").toString());
        var generation = new Generation();
        if (activeUsers.putIfAbsent(userId, generation) != null) throw new AiRequestException(429, "你已有一个回答正在生成");
        SseEmitter emitter = new SseEmitter(120000L);
        var cancellation = generation.cancellation;
        emitter.onCompletion(cancellation::cancel);
        emitter.onTimeout(cancellation::cancel);
        emitter.onError(error -> cancellation.cancel());
        try {
            workers.execute(() -> {
                long start = System.nanoTime();
                ScheduledFuture<?> deadline = timer.schedule(() -> {
                    cancellation.cancel();
                    try { emitter.send(SseEmitter.event().name("error").data(Map.of("message", "生成超时，请重试"))); }
                    catch (Exception ignored) { }
                    emitter.complete();
                }, 120, TimeUnit.SECONDS);
                try {
                    emitter.send(SseEmitter.event().name("ready").data(Map.of("requestId", generation.id)));
                    ChatModelGateway.Sink sink = (event, data) -> {
                        if (cancellation.cancelled()) throw new java.io.IOException("已取消");
                        var result = new HashMap<>(data);
                        if (event.equals("done")) result.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                        emitter.send(SseEmitter.event().name(event).data(result));
                    };
                    if (request.summaryMode()) {
                        summaries.generate(userId, request, gateway, sink, cancellation);
                    } else if (request.knowledgeMode()) {
                        String question = request.messages().get(request.messages().size() - 1).content();
                        var retrieval = request.documentVectorMode() ? documentVectors.retrieve(userId, question, cancellation)
                                : request.documentMode() ? documents.retrieve(userId, question)
                                : request.vectorMode() ? vectors.retrieve(userId, question, cancellation) : knowledge.retrieve(userId, question);
                        sink.send("sources", Map.of("items", retrieval.sources(), "retrieval", request.vectorMode() ? "vector-v1" : "keyword-v1",
                                "embeddingModel", request.documentVectorMode() ? documentVectors.model() : request.vectorMode() ? vectors.model() : "",
                                "scannedDocuments", retrieval.scannedDocuments(), "limited", retrieval.limited(),
                                "retrievalMs", retrieval.elapsedMs(), "sourceType", request.documentMode() ? "document" : "announcement"));
                        if (retrieval.sources().isEmpty()) {
                            sink.send("delta", Map.of("text", request.documentVectorMode()
                                    ? "你的有效个人文档索引中未找到足够相关的资料。请确认已上传并同步文档，或补充问题描述。"
                                    : request.documentMode()
                                    ? "在你的个人文档库中，未找到与问题匹配的资料。请先上传文档，或使用文档标题及明确关键词提问。"
                                    : request.vectorMode()
                                    ? "本次可访问的有效公告索引中，未找到足够相关的资料。请确认索引已同步，或补充问题描述。"
                                    : "在本次可访问的公告检索范围内，未找到与问题匹配的资料。请使用公告标题或明确关键词重新提问。"));
                            sink.send("done", Map.of("usage", Map.of("total_tokens", 0), "finishReason", "stop", "modelCalled", false));
                        } else {
                            gateway.streamGrounded(question, retrieval.sources(), sink, cancellation);
                        }
                    } else gateway.stream(request.messages(), sink, cancellation);
                } catch (Exception e) {
                    if (!cancellation.cancelled()) {
                        String message = e instanceof AiRequestException ? e.getMessage()
                                : request.vectorMode() ? "向量检索或生成失败，请检查本机向量服务、索引同步状态和模型配置" : "生成失败，请检查模型配置、网络或稍后重试";
                        try { emitter.send(SseEmitter.event().name("error").data(Map.of("message", message))); }
                        catch (Exception ignored) { }
                    }
                } finally {
                    deadline.cancel(false);
                    cancellation.cancel();
                    activeUsers.remove(userId, generation);
                    generation.finished.complete(null);
                    emitter.complete();
                }
            });
        } catch (RejectedExecutionException e) {
            activeUsers.remove(userId, generation);
            throw new AiRequestException(429, "AI 服务繁忙，请稍后重试");
        }
        response.setHeader("X-Accel-Buffering", "no");
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("Content-Type", "text/event-stream;charset=UTF-8").body(emitter);
    }

    @GetMapping("/knowledge/announcements/{id}")
    public Result<?> source(@PathVariable int id) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null || claims.get("id") == null) throw new AiRequestException(401, "请先登录");
        return Result.success(knowledge.source(Integer.parseInt(claims.get("id").toString()), id));
    }

    @GetMapping("/knowledge/index")
    public Result<?> indexStatus() { return Result.success(vectors.status(currentUser())); }

    @PostMapping("/knowledge/index")
    public Result<?> syncIndex() { return Result.success(vectors.sync(currentUser())); }

    @GetMapping("/knowledge/documents")
    public Result<?> documents() { return Result.success(documents.list(currentUser())); }

    @GetMapping("/groups")
    public Result<?> summaryGroups() { return Result.success(summaries.groups(currentUser())); }

    @GetMapping("/groups/{groupId}/messages/{id}")
    public Result<?> summarySource(@PathVariable int groupId, @PathVariable long id) {
        return Result.success(summaries.source(currentUser(), groupId, id));
    }

    @GetMapping("/knowledge/documents/index")
    public Result<?> documentIndexStatus() { return Result.success(documentVectors.status(currentUser())); }

    @PostMapping("/knowledge/documents/index")
    public Result<?> syncDocumentIndex() { return Result.success(documentVectors.sync(currentUser())); }

    @PostMapping(value = "/knowledge/documents", consumes = "multipart/form-data")
    public Result<?> uploadDocument(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return Result.success(documents.upload(currentUser(), file));
    }

    @GetMapping("/knowledge/documents/{id}")
    public Result<?> documentSource(@PathVariable int id) { return Result.success(documents.source(currentUser(), id)); }

    @DeleteMapping("/knowledge/documents/{id}")
    public Result<?> removeDocument(@PathVariable int id) { documents.remove(currentUser(), id); return Result.success(); }

    private int currentUser() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null || claims.get("id") == null) throw new AiRequestException(401, "请先登录");
        return Integer.parseInt(claims.get("id").toString());
    }

    @PostMapping("/chat/{requestId}/cancel")
    public Result<?> cancel(@PathVariable String requestId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null || claims.get("id") == null) throw new AiRequestException(401, "请先登录");
        int userId = Integer.parseInt(claims.get("id").toString());
        Generation generation = activeUsers.get(userId);
        // A late or repeated cancel must never cancel a newer request (or another user's request).
        if (generation == null || !generation.id.equals(requestId)) return Result.success();
        generation.cancellation.cancel();
        try {
            generation.finished.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiRequestException(503, "取消尚未确认，请稍后重试");
        } catch (ExecutionException | TimeoutException e) {
            throw new AiRequestException(503, "生成仍在释放资源，请稍后重试");
        }
        return Result.success();
    }

    @PreDestroy
    public void shutdown() { workers.shutdownNow(); timer.shutdownNow(); }

    @ExceptionHandler(AiRequestException.class)
    public ResponseEntity<?> handleAiRequest(AiRequestException e) {
        return ResponseEntity.status(e.status).body(Result.error(e.getMessage()));
    }

    static class AiRequestException extends RuntimeException {
        final int status;
        AiRequestException(int status, String message) { super(message); this.status = status; }
    }
}
