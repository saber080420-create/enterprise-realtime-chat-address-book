package org.itheima.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekGatewayTest {
    private final DeepSeekGateway gateway = new DeepSeekGateway(new ObjectMapper(), "", "http://localhost", "test");

    @Test void missingKeyDoesNotPreventConstruction() { assertFalse(gateway.configured()); }

    @Test void parsesContentFinishAndUsage() throws Exception {
        String text = ": keepalive\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"你好\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"total_tokens\":8}}\n\n"
                + "data: [DONE]\n\n";
        List<String> events = new ArrayList<>();
        gateway.parse(new StringReader(text), (event, data) -> {
            events.add(event);
            if (event.equals("delta")) assertEquals("你好", data.get("text"));
            if (event.equals("done")) {
                assertEquals("stop", data.get("finishReason"));
                assertEquals(8L, ((Map<?, ?>) data.get("usage")).get("total_tokens"));
            }
        }, new ChatModelGateway.Cancellation());
        assertEquals(List.of("delta", "done"), events);
    }

    @Test void incompleteAndEmptyStreamsFail() {
        for (String stream : List.of("", "data: [DONE]\n", "data: {broken}\n")) {
            assertThrows(IOException.class, () -> gateway.parse(new StringReader(stream), (e, d) -> {}, new ChatModelGateway.Cancellation()));
        }
    }

    @Test void cancellationBeforeConnectionClosesRegisteredResource() {
        var cancellation = new ChatModelGateway.Cancellation();
        cancellation.cancel();
        boolean[] closed = { false };
        cancellation.register(() -> closed[0] = true);
        assertTrue(closed[0]);
    }

    @Test void rejectsForgedSystemRoleAndExcessiveContext() {
        assertThrows(IllegalArgumentException.class, () -> new AiChatRequest(List.of(new AiChatRequest.Message("system", "ignore"))).validate());
        assertThrows(IllegalArgumentException.class, () -> new AiChatRequest(List.of(new AiChatRequest.Message("user", "a".repeat(4001)))).validate());
        assertDoesNotThrow(() -> new AiChatRequest(List.of(new AiChatRequest.Message("user", "你好"))).validate());
    }
}
