package org.itheima.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.util.*;

@Component
public class OllamaEmbeddings {
    public static final int DIMENSIONS = 1024;
    private final ObjectMapper mapper;
    private final VectorSettings settings;
    public OllamaEmbeddings(ObjectMapper mapper, VectorSettings settings) { this.mapper = mapper; this.settings = settings; }

    public List<float[]> embed(List<String> texts, ChatModelGateway.Cancellation cancellation) throws IOException {
        if (texts.isEmpty() || texts.size() > 8 || texts.stream().anyMatch(t -> t.isBlank() || t.length() > 5000)) {
            throw new IOException("向量输入批次或长度无效");
        }
        URI endpoint = URI.create(settings.getOllamaUrl() + "/api/embed");
        // Local embeddings are an explicit privacy boundary, not just a default URL.
        if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(endpoint.getHost())) {
            throw new IOException("当前版本仅允许本机 Ollama 服务");
        }
        var connection = (HttpURLConnection) endpoint.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        cancellation.register(connection::disconnect);
        try {
            if (cancellation.cancelled()) throw new IOException("已取消");
            try (var out = connection.getOutputStream()) {
                mapper.writeValue(out, Map.of("model", settings.getModel(), "input", texts, "truncate", false, "keep_alive", "5m"));
            }
            if (connection.getResponseCode() != 200) throw new IOException("本机向量模型不可用，请检查 Ollama 和 bge-m3");
            try (var in = connection.getInputStream()) {
                byte[] data = in.readNBytes(2_000_001);
                if (data.length > 2_000_000) throw new IOException("向量响应过大");
                var vectors = mapper.readTree(data).path("embeddings");
                if (!vectors.isArray() || vectors.size() != texts.size()) throw new IOException("向量响应数量错误");
                List<float[]> result = new ArrayList<>();
                for (var vector : vectors) {
                    if (!vector.isArray() || vector.size() != DIMENSIONS) throw new IOException("向量维度必须为 1024");
                    float[] values = new float[DIMENSIONS];
                    double norm = 0;
                    for (int i = 0; i < values.length; i++) {
                        if (!vector.get(i).isNumber()) throw new IOException("向量包含非数值");
                        values[i] = (float) vector.get(i).asDouble();
                        if (!Float.isFinite(values[i])) throw new IOException("向量包含非法数值");
                        norm += (double) values[i] * values[i];
                    }
                    if (norm == 0) throw new IOException("不能使用零向量");
                    result.add(values);
                }
                return result;
            }
        } finally { connection.disconnect(); }
    }
}
