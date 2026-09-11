package org.itheima.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OllamaEmbeddingsTest {
    @Test void verifiesBatchDimensionsAndDisablesSilentTruncation() throws Exception {
        var json = new ObjectMapper();
        var request = new AtomicReference<String>();
        var response = new AtomicReference<byte[]>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] data = response.get();
            exchange.sendResponseHeaders(200, data.length);
            try (var out = exchange.getResponseBody()) { out.write(data); }
        });
        server.start();
        try {
            var settings = new VectorSettings(); settings.setOllamaUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var client = new OllamaEmbeddings(json, settings);
            float[] vector = new float[1024]; vector[0] = 1;
            response.set(json.writeValueAsBytes(Map.of("embeddings", List.of(vector))));
            assertEquals(1024, client.embed(List.of("测试"), new ChatModelGateway.Cancellation()).get(0).length);
            assertFalse(json.readTree(request.get()).path("truncate").asBoolean());
            assertEquals("bge-m3", json.readTree(request.get()).path("model").asText());
            response.set(json.writeValueAsBytes(Map.of("embeddings", List.of(List.of(1, 2)))));
            assertThrows(java.io.IOException.class, () -> client.embed(List.of("测试"), new ChatModelGateway.Cancellation()));
            response.set(json.writeValueAsBytes(Map.of("embeddings", List.of(new float[1024]))));
            assertThrows(java.io.IOException.class, () -> client.embed(List.of("测试"), new ChatModelGateway.Cancellation()));
            response.set(json.writeValueAsBytes(Map.of("embeddings", List.of())));
            assertThrows(java.io.IOException.class, () -> client.embed(List.of("测试"), new ChatModelGateway.Cancellation()));
        } finally { server.stop(0); }
    }

    @Test void rejectsRemoteEmbeddingHostBeforeSendingText() {
        var settings = new VectorSettings(); settings.setOllamaUrl("https://example.com");
        var client = new OllamaEmbeddings(new ObjectMapper(), settings);
        assertThrows(java.io.IOException.class, () -> client.embed(List.of("测试"), new ChatModelGateway.Cancellation()));
    }
}
