package org.itheima.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;

@Service
public class DocumentKnowledgeService {
    private final DocumentKnowledgeMapper mapper;
    public DocumentKnowledgeService(DocumentKnowledgeMapper mapper) { this.mapper = mapper; }

    @Transactional
    public Map<String, Object> upload(int userId, MultipartFile file) {
        KnowledgeDocument document = parse(file);
        if (mapper.lockOwner(userId) == null) throw error(403, "当前账号不能上传文档");
        if (mapper.count(userId) >= 50) throw error(409, "个人知识库最多保存 50 篇有效文档，请先移出不需要的文档");
        document.setOwnerId(userId);
        mapper.insert(document);
        return summary(document);
    }

    public List<Map<String, Object>> list(int userId) {
        return mapper.candidates(userId).stream().limit(50).map(DocumentKnowledgeService::summary).toList();
    }

    public Map<String, Object> source(int userId, int id) {
        var document = mapper.source(userId, id);
        if (document == null) throw error(404, "文档不存在或当前无权查看");
        return Map.of("id", id, "title", document.getTitle(), "content", document.getContent(),
                "notice", "个人文档，仅上传者可见；查看时重新校验账号状态和所有权。以纯文本展示，不执行 Markdown 或 HTML。");
    }

    public void remove(int userId, int id) {
        if (mapper.remove(userId, id) != 1) throw error(404, "文档不存在或当前无权操作");
    }

    private record Hit(KnowledgeDocument document, String text, int chunk, double score) {}

    public AnnouncementKnowledgeService.Retrieval retrieve(int userId, String question) {
        long start = System.nanoTime();
        var all = mapper.candidates(userId);
        var documents = all.stream().limit(50).toList();
        var query = AnnouncementKnowledgeService.plainTerms(question);
        List<Hit> hits = new ArrayList<>();
        for (var document : documents) {
            var title = AnnouncementKnowledgeService.plainTerms(document.getTitle());
            var chunks = chunks(document.getContent());
            for (int i = 0; i < chunks.size(); i++) {
                var terms = AnnouncementKnowledgeService.plainTerms(chunks.get(i));
                double score = query.stream().mapToDouble(t -> (title.contains(t) ? 3 : 0) + (terms.contains(t) ? 1 : 0)).sum();
                if (score > 0) hits.add(new Hit(document, chunks.get(i), i, score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed()
                .thenComparing(h -> h.document().getId(), Comparator.reverseOrder()).thenComparingInt(Hit::chunk));
        List<AnnouncementKnowledgeService.Evidence> evidence = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (var hit : hits) {
            if (!used.add(hit.document().getId())) continue;
            var current = mapper.source(userId, hit.document().getId());
            if (current == null || !Objects.equals(current.getContentHash(), hit.document().getContentHash())
                    || !Objects.equals(current.getTitle(), hit.document().getTitle())
                    || !Objects.equals(current.getContent(), hit.document().getContent())) continue;
            evidence.add(new AnnouncementKnowledgeService.Evidence(evidence.size() + 1, current.getId(),
                    current.getTitle(), hit.text(), hit.chunk(), "document"));
            if (evidence.size() == 4) break;
        }
        return new AnnouncementKnowledgeService.Retrieval(List.copyOf(evidence), documents.size(), all.size() > 50,
                (System.nanoTime() - start) / 1_000_000);
    }

    static KnowledgeDocument parse(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 80_000) throw error(400, "文档需为 1 至 80000 字节");
        String name = file.getOriginalFilename();
        if (name == null || name.length() > 200 || name.contains("/") || name.contains("\\")
                || name.codePoints().anyMatch(Character::isISOControl)
                || !(name.toLowerCase(Locale.ROOT).endsWith(".txt") || name.toLowerCase(Locale.ROOT).endsWith(".md")))
            throw error(400, "仅支持文件名不超过 200 字符的 TXT/Markdown 文档");
        try {
            byte[] bytes;
            try (var input = file.getInputStream()) { bytes = input.readNBytes(80_001); }
            if (bytes.length > 80_000) throw error(400, "文档超过 80000 字节");
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            text = text.replace("\r\n", "\n").replace('\r', '\n');
            if (text.isBlank() || text.length() > 20_000 || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
                throw error(400, "正文需为 1 至 20000 字符的 UTF-8 纯文本，不支持二进制内容");
            var document = new KnowledgeDocument();
            document.setTitle(name); document.setContent(text);
            document.setContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))));
            return document;
        } catch (java.io.IOException e) { throw error(400, "文件读取失败，请使用 UTF-8 编码的 TXT/Markdown"); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    // Plain text must retain comparisons/code such as '< 3'; never run HTML stripping on uploaded text.
    static List<String> chunks(String text) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += 500) {
            int end = Math.min(start + 600, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
        }
        return chunks;
    }

    private static Map<String, Object> summary(KnowledgeDocument d) {
        return Map.of("id", d.getId(), "title", d.getTitle(), "characters", d.getContent().length());
    }
    private static AiChatController.AiRequestException error(int status, String message) {
        return new AiChatController.AiRequestException(status, message);
    }
}
