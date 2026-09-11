package org.itheima.ai;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ChatModelGateway {
    boolean configured();
    String model();
    void stream(List<AiChatRequest.Message> messages, Sink sink, Cancellation cancellation) throws IOException;

    default void streamGrounded(String question, List<AnnouncementKnowledgeService.Evidence> sources,
                                Sink sink, Cancellation cancellation) throws IOException {
        throw new IOException("当前模型适配器不支持知识问答");
    }

    interface Sink {
        
        void send(String event, Map<String, Object> data) throws IOException;
    }

    default void streamSummary(List<GroupSummaryService.Source> sources, Sink sink, Cancellation cancellation) throws IOException {
        throw new IOException("当前模型适配器不支持摘要");
    }

    /** Register cancellation before opening the upstream connection; cancellation is idempotent. */
    final class Cancellation {
        private boolean cancelled;
        private Runnable close = () -> {};
        public synchronized void register(Runnable action) {
            if (cancelled) action.run();
            else close = action;
        }
        public synchronized void cancel() {
            if (!cancelled) { cancelled = true; close.run(); }
        }
        public synchronized boolean cancelled() { return cancelled; }
    }
}
