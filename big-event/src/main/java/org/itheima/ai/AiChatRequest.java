package org.itheima.ai;

import java.util.List;
import java.util.Set;

public record AiChatRequest(List<Message> messages, String mode, Integer groupId, String start, String end) {
    public AiChatRequest(List<Message> messages, String mode) { this(messages, mode, null, null, null); }
    public boolean summaryMode() { return "group-summary".equals(mode); }
    public AiChatRequest(List<Message> messages) { this(messages, "general"); }
    public boolean knowledgeMode() { return "announcements".equals(mode) || vectorMode() || documentMode(); }
    public boolean documentMode() { return "documents".equals(mode) || documentVectorMode(); }
    public boolean documentVectorMode() { return "documents-vector".equals(mode); }
    public boolean vectorMode() { return "announcements-vector".equals(mode) || documentVectorMode(); }
    public record Message(String role, String content) {}

    public void validate() {
        if (summaryMode()) GroupSummaryService.range(groupId, start, end);
        if (mode != null && !Set.of("general", "announcements", "announcements-vector", "documents", "documents-vector", "group-summary").contains(mode)) {
            throw new IllegalArgumentException("不支持的问答模式");
        }
        if (messages == null || messages.isEmpty() || messages.size() > 20) {
            throw new IllegalArgumentException("对话需包含 1 至 20 条消息");
        }
        int total = 0;
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            String expected = i % 2 == 0 ? "user" : "assistant";
            if (message == null || !expected.equals(message.role()) || message.content() == null
                    || message.content().isBlank() || message.content().length() > 4000) {
                throw new IllegalArgumentException("消息必须按用户/助手交替排列，且每条为 1 至 4000 字符");
            }
            total += message.content().length();
        }
        if (total > 16000 || !"user".equals(messages.get(messages.size() - 1).role())) {
            throw new IllegalArgumentException("对话总长度不能超过 16000 字符，且必须以用户消息结束");
        }
    }
}
