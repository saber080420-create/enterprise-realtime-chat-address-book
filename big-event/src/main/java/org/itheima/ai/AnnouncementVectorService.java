package org.itheima.ai;

import jakarta.annotation.PreDestroy;
import org.itheima.pojo.Announcement;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AnnouncementVectorService {
    private final AnnouncementKnowledgeMapper mapper;
    private final OllamaEmbeddings embeddings;
    private final PgVectorStore store;
    private final VectorSettings settings;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Job job;
    private static class Job {
        int owner;
        volatile String state = "running";
        volatile String message = "正在同步当前用户可见公告";
        volatile int processed, total, reused;
        final ChatModelGateway.Cancellation cancellation = new ChatModelGateway.Cancellation();
    }
    public AnnouncementVectorService(AnnouncementKnowledgeMapper mapper, OllamaEmbeddings embeddings,
                                     PgVectorStore store, VectorSettings settings) {
        this.mapper = mapper; this.embeddings = embeddings; this.store = store; this.settings = settings;
    }
    public boolean enabled() { return settings.isEnabled(); }
    public String model() { return settings.getModel(); }

    public synchronized Map<String, Object> status(int userId) {
        if (job == null || job.owner != userId) return Map.of("state", "idle", "enabled", enabled());
        return Map.of("state", job.state, "message", job.message, "processed", job.processed,
                "total", job.total, "reused", job.reused, "enabled", enabled());
    }

    public synchronized Map<String, Object> sync(int userId) {
        if (!enabled()) throw new AiChatController.AiRequestException(503, "请先启用 AI_RAG_ENABLED 并启动向量服务");
        if (job != null && job.state.equals("running")) throw new AiChatController.AiRequestException(429, "已有索引同步任务，请稍后重试");
        Job next = new Job(); next.owner = userId; job = next;
        worker.execute(() -> runSync(next));
        return status(userId);
    }

    private void runSync(Job task) {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        try {
            List<Announcement> documents = mapper.candidates(task.owner).stream().limit(200).toList();
            task.total = documents.size();
            for (var document : documents) {
                if (System.nanoTime() > deadline || task.cancellation.cancelled()) throw new IllegalStateException("同步超时");
                var current = mapper.visibleSource(task.owner, document.getId());
                if (current == null) { task.processed++; continue; }
                String hash = fingerprint(current);
                var chunks = AnnouncementKnowledgeService.chunks(current.getContent());
                if (store.current(current.getId(), hash, chunks.size())) {
                    task.reused++; task.processed++; continue;
                }
                List<float[]> vectors = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i += 8) {
                    if (System.nanoTime() > deadline || task.cancellation.cancelled()) throw new IllegalStateException("同步超时");
                    var batch = chunks.subList(i, Math.min(i + 8, chunks.size())).stream()
                            .map(text -> AnnouncementKnowledgeService.clean(current.getTitle()) + "\n" + text).toList();
                    vectors.addAll(embeddings.embed(batch, task.cancellation));
                }
                var latest = mapper.visibleSource(task.owner, current.getId());
                if (latest != null && fingerprint(latest).equals(hash)) store.replace(current.getId(), hash, vectors);
                task.processed++;
            }
            task.message = "同步完成（仅本次可见范围；变更后请再次同步）";
            task.state = "completed";
        } catch (Exception e) {
            task.message = "同步失败或超时，请检查本机 Ollama、bge-m3、PGVector；已完成部分保留，可再次同步";
            task.state = "failed";
        }
    }

    public AnnouncementKnowledgeService.Retrieval retrieve(int userId, String query,
            ChatModelGateway.Cancellation cancellation) throws Exception {
        if (!enabled()) throw new AiChatController.AiRequestException(503, "向量检索未启用，请设置 AI_RAG_ENABLED");
        long start = System.nanoTime();
        var all = mapper.candidates(userId);
        var documents = all.stream().limit(200).toList();
        if (documents.isEmpty()) return new AnnouncementKnowledgeService.Retrieval(List.of(), 0, false, 0);
        List<PgVectorStore.Candidate> allowed = new ArrayList<>();
        var coverage = store.coverage(documents.stream().map(d -> new PgVectorStore.Candidate(d.getId(), fingerprint(d))).toList());
        int indexed = 0;
        for (var d : documents) {
            String hash = fingerprint(d);
            int count = AnnouncementKnowledgeService.chunks(d.getContent()).size();
            if (count > 0 && coverage.getOrDefault(d.getId(), 0) == count) {
                indexed++; allowed.add(new PgVectorStore.Candidate(d.getId(), hash));
            }
        }
        if (allowed.isEmpty()) throw new AiChatController.AiRequestException(503, "可见公告尚未建立有效向量索引，请先同步");
        var queryVector = embeddings.embed(List.of(query), cancellation).get(0);
        var matches = store.search(allowed, queryVector);
        List<AnnouncementKnowledgeService.Evidence> sources = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (var hit : matches) {
            if (!Double.isFinite(hit.score()) || hit.score() < settings.getMinScore() || !used.add(hit.id())) continue;
            // Defense in depth: only the exact authorized revision may provide context.
            if (!allowed.contains(new PgVectorStore.Candidate(hit.id(), hit.fingerprint()))) continue;
            var current = mapper.visibleSource(userId, hit.id());
            if (current == null || !fingerprint(current).equals(hit.fingerprint())) continue;
            var chunks = AnnouncementKnowledgeService.chunks(current.getContent());
            if (hit.chunkIndex() < 0 || hit.chunkIndex() >= chunks.size()) continue;
            sources.add(new AnnouncementKnowledgeService.Evidence(sources.size() + 1, hit.id(),
                    AnnouncementKnowledgeService.clean(current.getTitle()), chunks.get(hit.chunkIndex()), hit.chunkIndex()));
            if (sources.size() == 4) break;
        }
        return new AnnouncementKnowledgeService.Retrieval(List.copyOf(sources), indexed,
                all.size() > 200 || indexed < documents.size(), (System.nanoTime() - start) / 1_000_000);
    }

    static String fingerprint(Announcement a) {
        try {
            StringBuilder text = new StringBuilder();
            for (Object value : Arrays.asList(a.getId(), a.getType(), a.getDepartmentId(), a.getStatus(), a.getTitle(), a.getContent())) {
                if (value == null) text.append("-1:");
                else { String field = value.toString(); text.append(field.length()).append(':').append(field); }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @PreDestroy public synchronized void shutdown() { if (job != null) job.cancellation.cancel(); worker.shutdownNow(); }
}
