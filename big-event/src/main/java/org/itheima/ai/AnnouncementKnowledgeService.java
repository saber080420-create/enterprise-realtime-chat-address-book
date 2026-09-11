package org.itheima.ai;

import org.itheima.pojo.Announcement;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.util.*;
import java.util.regex.Pattern;

/** A bounded lexical retrieval baseline. No embedding or vector database is claimed here. */
@Service
public class AnnouncementKnowledgeService {
    private static final int CHUNK_SIZE = 600;
    private static final int STRIDE = 500;
    private static final Pattern WORDS = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP = Set.of("什么", "如何", "怎么", "是否", "请问", "可以", "我们", "你们", "这个", "那个", "哪些", "多少", "一下", "告诉", "有关", "相关", "关于");
    private final AnnouncementKnowledgeMapper mapper;

    public AnnouncementKnowledgeService(AnnouncementKnowledgeMapper mapper) { this.mapper = mapper; }

    public record Evidence(int number, int announcementId, String title, String excerpt, int chunkIndex, String sourceType) {
        public Evidence(int number, int announcementId, String title, String excerpt, int chunkIndex) {
            this(number, announcementId, title, excerpt, chunkIndex, "announcement");
        }
    }
    public record Retrieval(List<Evidence> sources, int scannedDocuments, boolean limited, long elapsedMs) {}
    private record Hit(Announcement document, String text, int chunk, double score) {}

    public Retrieval retrieve(int userId, String question) {
        long start = System.nanoTime();
        List<Announcement> all = mapper.candidates(userId);
        boolean limited = all.size() > 200;
        List<Announcement> documents = all.stream().limit(200).toList();
        Set<String> query = terms(question);
        List<Hit> hits = new ArrayList<>();
        for (Announcement document : documents) {
            List<String> chunks = chunks(document.getContent());
            Set<String> titleTerms = terms(document.getTitle());
            for (int i = 0; i < chunks.size(); i++) {
                Set<String> contentTerms = terms(chunks.get(i));
                long matches = query.stream().filter(t -> titleTerms.contains(t) || contentTerms.contains(t)).count();
                // Require lexical overlap; this is not a calibrated relevance probability.
                if (matches == 0) continue;
                double score = query.stream().mapToDouble(t -> (titleTerms.contains(t) ? 3 : 0) + (contentTerms.contains(t) ? 1 : 0)).sum();
                hits.add(new Hit(document, chunks.get(i), i, score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed()
                .thenComparing(h -> h.document().getId(), Comparator.reverseOrder()).thenComparingInt(Hit::chunk));
        List<Evidence> selected = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (Hit hit : hits) {
            int id = hit.document().getId();
            if (!used.add(id)) continue; // One best chunk per document, max four sources.
            Announcement current = mapper.visibleSource(userId, id);
            // Recheck visibility and contents after retrieval. Revoked/edited sources cannot be sent stale.
            if (current == null || !Objects.equals(current.getTitle(), hit.document().getTitle())
                    || !Objects.equals(current.getContent(), hit.document().getContent())) continue;
            selected.add(new Evidence(selected.size() + 1, id, clean(current.getTitle()), hit.text(), hit.chunk()));
            if (selected.size() == 4) break;
        }
        return new Retrieval(List.copyOf(selected), documents.size(), limited, (System.nanoTime() - start) / 1_000_000);
    }

    public Map<String, Object> source(int userId, int id) {
        Announcement source = mapper.visibleSource(userId, id);
        if (source == null) throw new AiChatController.AiRequestException(404, "来源不存在或当前无权查看");
        return Map.of("id", id, "title", clean(source.getTitle()), "content", clean(source.getContent()),
                "notice", "最多展示原文前 20000 字符；查看时重新校验权限。内容可能在回答后更新。");
    }

    static String clean(String text) {
        if (text == null) return "";
        // Never render source HTML. Strip executable blocks before extracting searchable text.
        return HtmlUtils.htmlUnescape(text.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>", " ")
                .replaceAll("<[^>]*>", " ")).replaceAll("\\s+", " ").trim();
    }

    static List<String> chunks(String raw) {
        String text = clean(raw);
        List<String> result = new ArrayList<>();
        for (int start = 0; start < text.length(); start += STRIDE) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            result.add(text.substring(start, end));
            if (end == text.length()) break;
        }
        return result;
    }

    static Set<String> terms(String raw) {
        return plainTerms(clean(raw));
    }

    static Set<String> plainTerms(String raw) {
        Set<String> terms = new HashSet<>();
        var matcher = WORDS.matcher(raw.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.codePoints().allMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
                int[] cps = word.codePoints().toArray();
                for (int i = 0; i + 1 < cps.length; i++) terms.add(new String(cps, i, 2));
            } else terms.add(word);
        }
        terms.removeAll(STOP);
        return terms;
    }
}
