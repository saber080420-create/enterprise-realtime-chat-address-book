package org.itheima.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

@Service
public class DocumentVectorService {
    private final DocumentKnowledgeMapper mapper;
    private final OllamaEmbeddings embeddings;
    private final PgVectorStore store;
    private final VectorSettings settings;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Job job;
    private static class Job {
        int owner;
        volatile String state = "running", message = "正在同步个人文档索引";
        volatile int processed, total, reused, skipped;
        final ChatModelGateway.Cancellation cancellation = new ChatModelGateway.Cancellation();
    }
    @Autowired
    public DocumentVectorService(DocumentKnowledgeMapper mapper, OllamaEmbeddings embeddings, VectorSettings settings) {
        this(mapper, embeddings, new PgVectorStore(settings, PgVectorStore.Resource.DOCUMENT), settings);
    }
    DocumentVectorService(DocumentKnowledgeMapper mapper, OllamaEmbeddings embeddings, PgVectorStore store, VectorSettings settings) {
        this.mapper = mapper; this.embeddings = embeddings; this.store = store; this.settings = settings;
    }
    public boolean enabled() { return settings.isEnabled(); }
    public String model() { return settings.getModel(); }
    public synchronized Map<String, Object> status(int userId) {
        if (job == null || job.owner != userId) return Map.of("state", "idle", "enabled", enabled());
        return Map.of("state", job.state, "message", job.message, "processed", job.processed,
                "total", job.total, "reused", job.reused, "skipped", job.skipped, "enabled", enabled());
    }
    public synchronized Map<String, Object> sync(int userId) {
        requireEnabled();
        if (job != null && job.state.equals("running")) throw new AiChatController.AiRequestException(429, "已有文档索引同步任务，请稍后重试");
        Job next = new Job(); next.owner = userId; job = next;
        worker.execute(() -> runSync(next));
        return status(userId);
    }
    private void runSync(Job task) {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        try {
            var documents = mapper.candidates(task.owner).stream().limit(50).toList();
            task.total = documents.size();
            for (var document : documents) {
                checkDeadline(task, deadline);
                var current = mapper.source(task.owner, document.getId());
                if (current == null) { task.skipped++; task.processed++; continue; }
                String hash = fingerprint(current);
                var chunks = DocumentKnowledgeService.chunks(current.getContent());
                if (store.current(current.getId(), hash, chunks.size())) { task.reused++; task.processed++; continue; }
                List<float[]> vectors = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i += 8) {
                    checkDeadline(task, deadline);
                    vectors.addAll(embeddings.embed(chunks.subList(i, Math.min(i + 8, chunks.size())).stream()
                            .map(text -> current.getTitle() + "\n" + text).toList(), task.cancellation));
                }
                checkDeadline(task, deadline);
                var latest = mapper.source(task.owner, current.getId());
                if (latest != null && fingerprint(latest).equals(hash)) store.replace(current.getId(), hash, vectors);
                else task.skipped++;
                task.processed++;
            }
            task.message = "文档同步完成（仅本次有效范围；新增文档后请再次同步）";
            task.state = "completed";
        } catch (Exception e) {
            task.message = "文档同步失败或超时，请检查 Ollama、bge-m3、PGVector 文档表；已完成部分保留，可重试";
            task.state = "failed";
        }
    }
    private void checkDeadline(Job task, long deadline) {
        if (System.nanoTime() > deadline || task.cancellation.cancelled()) throw new IllegalStateException("同步超时或已取消");
    }
    private void requireEnabled() {
        if (!enabled()) throw new AiChatController.AiRequestException(503, "文档向量检索未启用，请设置 AI_RAG_ENABLED 并启动向量服务");
    }
    public AnnouncementKnowledgeService.Retrieval retrieve(int userId, String query, ChatModelGateway.Cancellation cancellation) throws Exception {
        requireEnabled();
        long start = System.nanoTime();
        var all = mapper.candidates(userId);
        var documents = all.stream().limit(50).toList();
        if (documents.isEmpty()) return new AnnouncementKnowledgeService.Retrieval(List.of(), 0, false, 0);
        var coverage = store.coverage(documents.stream().map(d -> new PgVectorStore.Candidate(d.getId(), fingerprint(d))).toList());
        Set<PgVectorStore.Candidate> allowed = new LinkedHashSet<>();
        for (var d : documents) {
            int count = DocumentKnowledgeService.chunks(d.getContent()).size();
            if (count > 0 && coverage.getOrDefault(d.getId(), 0) == count) allowed.add(new PgVectorStore.Candidate(d.getId(), fingerprint(d)));
        }
        if (allowed.isEmpty()) throw new AiChatController.AiRequestException(503, "个人文档尚未建立有效向量索引，请先同步个人文档索引");
        var queryVector = embeddings.embed(List.of(query), cancellation).get(0);
        var matches = store.search(List.copyOf(allowed), queryVector);
        List<AnnouncementKnowledgeService.Evidence> sources = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (var hit : matches) {
            if (cancellation.cancelled()) throw new java.io.IOException("已取消");
            if (!Double.isFinite(hit.score()) || hit.score() < settings.getMinScore() || used.contains(hit.id())
                    || !allowed.contains(new PgVectorStore.Candidate(hit.id(), hit.fingerprint()))) continue;
            // MySQL is authoritative, including removal/ownership changes that occurred after vector search.
            var current = mapper.source(userId, hit.id());
            if (current == null || !fingerprint(current).equals(hit.fingerprint())) continue;
            var chunks = DocumentKnowledgeService.chunks(current.getContent());
            if (hit.chunkIndex() < 0 || hit.chunkIndex() >= chunks.size()) continue;
            sources.add(new AnnouncementKnowledgeService.Evidence(sources.size() + 1, hit.id(), current.getTitle(),
                    chunks.get(hit.chunkIndex()), hit.chunkIndex(), "document"));
            used.add(hit.id());
            if (sources.size() == 4) break;
        }
        return new AnnouncementKnowledgeService.Retrieval(List.copyOf(sources), allowed.size(),
                all.size() > 50 || allowed.size() < documents.size(), (System.nanoTime() - start) / 1_000_000);
    }
    static String fingerprint(KnowledgeDocument d) {
        try {
            StringBuilder text = new StringBuilder();
            for (Object value : Arrays.asList(d.getId(), d.getOwnerId(), d.getTitle(), d.getContentHash(), d.getContent())) {
                if (value == null) text.append("-1:");
                else { String field = value.toString(); text.append(field.length()).append(':').append(field); }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @PreDestroy public synchronized void shutdown() { if (job != null) job.cancellation.cancel(); worker.shutdownNow(); }
}
