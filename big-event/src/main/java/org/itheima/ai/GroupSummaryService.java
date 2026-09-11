package org.itheima.ai;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import org.itheima.pojo.ChatMessage;

@Service
public class GroupSummaryService {
    private final GroupSummaryMapper mapper;
    private final ObjectMapper json;
    public GroupSummaryService(GroupSummaryMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }
    public record Source(String id, int senderId, String time, String content) {}
    public record Point(String text, List<String> evidenceIds) {}
    public record Action(String task, String owner, String deadline, List<String> evidenceIds) {}
    public record Output(List<Point> points, List<Action> actions) {}

    public Object groups(int userId) { return mapper.groups(userId); }
    private void requireGroup(int userId, int groupId) {
        if (mapper.group(userId, groupId) == null) throw error(404, "群聊不存在或你已无权访问");
    }
    public Object source(int userId, int groupId, long id) {
        var row = mapper.source(userId, groupId, id);
        if (row == null) throw error(404, "消息已撤回、删除或当前无权查看");
        return Map.of("title", "群消息 #" + row.getId(), "content", row.getContent(),
                "notice", "发送者 ID " + row.getSenderId() + " · " + row.getCreateTime() + "；查看时重新校验权限，最多显示前 2001 字符。");
    }
    public void generate(int userId, AiChatRequest request, ChatModelGateway gateway,
                         ChatModelGateway.Sink sink, ChatModelGateway.Cancellation cancellation) throws Exception {
        var range = range(request.groupId(), request.start(), request.end());
        int groupId = request.groupId();
        requireGroup(userId, groupId);
        var rows = mapper.messages(userId, groupId, range[0], range[1]);
        List<ChatMessage> selected = new ArrayList<>(); int chars = 0; boolean limited = rows.size() > 100;
        for (var row : rows.stream().limit(100).toList()) {
            if (row.getContent() == null || row.getContent().isBlank()) continue;
            if (row.getContent().length() > 2000 || chars + row.getContent().length() > 16000) { limited = true; continue; }
            selected.add(row); chars += row.getContent().length();
        }
        Collections.reverse(selected);
        recheck(userId, groupId, selected);
        var sources = selected.stream().map(r -> new Source(r.getId().toString(), r.getSenderId(), r.getCreateTime().toString(), r.getContent())).toList();
        if (sources.isEmpty()) {
            sink.send("summary", Map.of("points", List.of(), "actions", List.of(), "sources", List.of(), "limited", limited, "groupId", groupId));
            sink.send("delta", Map.of("text", "所选范围内没有可用于总结的文本消息（仅当前成员自己的消息副本；过长消息会跳过）。"));
            sink.send("done", Map.of("finishReason", "stop", "usage", Map.of("total_tokens", 0), "modelCalled", false)); return;
        }
        StringBuilder buffer = new StringBuilder(); Map<String, Object> completion = new HashMap<>();
        gateway.streamSummary(sources, (event, data) -> {
            if (cancellation.cancelled()) throw new java.io.IOException("已取消");
            if (event.equals("delta")) {
                Object value = data.get("text"); if (!(value instanceof String text)) throw new java.io.IOException("非法摘要片段");
                buffer.append(text); if (buffer.length() > 24000) throw new java.io.IOException("摘要过长");
            } else if (event.equals("done")) completion.putAll(data);
            else if (event.equals("error")) throw new java.io.IOException("摘要生成失败");
        }, cancellation);
        if (!"stop".equals(completion.get("finishReason"))) throw error(502, "摘要输出不完整，请缩小时间范围后重试");
        var output = validate(buffer.toString(), sources);
        recheck(userId, groupId, selected);
        sink.send("summary", Map.of("points", output.points(), "actions", output.actions(), "sources", sources,
                "limited", limited, "groupId", groupId));
        sink.send("delta", Map.of("text", "群聊摘要已生成，请核对原消息；行动项只是建议，未创建任务或发送通知。"));
        sink.send("done", completion);
    }
    private void recheck(int userId, int groupId, List<ChatMessage> selected) {
        requireGroup(userId, groupId);
        for (var row : selected) {
            var current = mapper.source(userId, groupId, row.getId());
            if (current == null || !Objects.equals(row.getContent(), current.getContent())
                    || !Objects.equals(row.getContentVersion(), current.getContentVersion())
                    || !Objects.equals(row.getSenderId(), current.getSenderId())
                    || !Objects.equals(row.getCreateTime(), current.getCreateTime()))
                throw error(409, "消息或群权限已变化，本次摘要不展示，请重新生成");
        }
    }
    static LocalDateTime[] range(Integer groupId, String start, String end) {
        try {
            var a = LocalDateTime.parse(start); var b = LocalDateTime.parse(end);
            if (groupId == null || groupId <= 0 || !a.isBefore(b) || Duration.between(a, b).compareTo(Duration.ofDays(7)) > 0)
                throw new IllegalArgumentException();
            return new LocalDateTime[]{a, b};
        } catch (Exception e) { throw error(400, "请选择群聊和有效起止时间，单次最多 7 天（北京时间，结束时间不包含）"); }
    }
    Output validate(String raw, List<Source> sources) {
        try {
            JsonNode root = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(raw);
            fields(root, Set.of("points", "actions"));
            Map<String, Source> available = new HashMap<>(); sources.forEach(s -> available.put(s.id(), s));
            List<Point> points = new ArrayList<>(); List<Action> actions = new ArrayList<>();
            for (var item : array(root.get("points"))) {
                fields(item, Set.of("text", "evidenceIds"));
                points.add(new Point(text(item.get("text"), 600), evidence(item.get("evidenceIds"), available)));
            }
            for (var item : array(root.get("actions"))) {
                fields(item, Set.of("task", "owner", "deadline", "evidenceIds"));
                var ids = evidence(item.get("evidenceIds"), available);
                String owner = nullable(item.get("owner"), 100), deadline = nullable(item.get("deadline"), 100);
                for (String value : Arrays.asList(owner, deadline)) {
                    if (value != null && ids.stream().noneMatch(id -> available.get(id).content().contains(value))) throw new IllegalArgumentException();
                }
                actions.add(new Action(text(item.get("task"), 600), owner, deadline, ids));
            }
            return new Output(List.copyOf(points), List.copyOf(actions));
        } catch (Exception e) { throw error(502, "摘要结构或引用校验失败，请缩小时间范围后重试"); }
    }
    private static void fields(JsonNode n, Set<String> fields) {
        if (n == null || !n.isObject() || n.size() != fields.size()) throw new IllegalArgumentException();
        n.fieldNames().forEachRemaining(key -> { if (!fields.contains(key)) throw new IllegalArgumentException(); });
    }
    private static JsonNode array(JsonNode n) { if (n == null || !n.isArray() || n.size() > 8) throw new IllegalArgumentException(); return n; }
    private static String text(JsonNode n, int max) {
        if (n == null || !n.isTextual() || n.textValue().isBlank() || n.textValue().length() > max) throw new IllegalArgumentException(); return n.textValue();
    }
    private static String nullable(JsonNode n, int max) { return n != null && n.isNull() ? null : text(n, max); }
    private static List<String> evidence(JsonNode n, Map<String, Source> available) {
        array(n); if (n.isEmpty()) throw new IllegalArgumentException();
        List<String> ids = new ArrayList<>();
        for (var id : n) { String value = text(id, 30); if (!available.containsKey(value) || ids.contains(value)) throw new IllegalArgumentException(); ids.add(value); }
        return List.copyOf(ids);
    }
    private static AiChatController.AiRequestException error(int status, String text) { return new AiChatController.AiRequestException(status, text); }
}
