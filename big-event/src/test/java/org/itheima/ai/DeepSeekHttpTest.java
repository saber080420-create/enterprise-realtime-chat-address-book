package org.itheima.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekHttpTest {
    @Test void postsServerOwnedPromptAndParsesRealHttpStream() throws Exception {
        var mapper = new ObjectMapper();
        var body = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write(("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"choices\":[],\"usage\":{\"total_tokens\":5}}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            var gateway = new DeepSeekGateway(mapper, "test-only-key", "http://127.0.0.1:" + server.getAddress().getPort(), "test");
            List<String> events = new ArrayList<>();
            gateway.stream(List.of(new AiChatRequest.Message("user", "你好")), (event, data) -> events.add(event), new ChatModelGateway.Cancellation());
            assertEquals(List.of("delta", "done"), events);
            assertEquals("Bearer test-only-key", auth.get());
            var request = mapper.readTree(body.get());
            assertEquals("system", request.path("messages").path(0).path("role").asText());
            assertEquals("user", request.path("messages").path(1).path("role").asText());
            assertTrue(request.path("stream").asBoolean());
            assertEquals("disabled", request.path("thinking").path("type").asText());
            gateway.streamSummary(List.of(new GroupSummaryService.Source("1", 2, "2026-09-08T10:00", "小王周五提交报告")),
                    (event, data) -> {}, new ChatModelGateway.Cancellation());
            var summaryRequest = mapper.readTree(body.get());
            assertTrue(summaryRequest.path("messages").path(0).path("content").asText().contains("evidenceIds"));
            assertTrue(summaryRequest.path("messages").path(0).path("content").asText().contains("低信任"));
            assertEquals("1", mapper.readTree(summaryRequest.path("messages").path(1).path("content").asText()).get(0).path("id").asText());
        } finally { server.stop(0); }
    }
}
