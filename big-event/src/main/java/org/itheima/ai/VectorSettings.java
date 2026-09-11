package org.itheima.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

@Component
@ConfigurationProperties(prefix = "ai.rag")
@Getter @Setter
@Validated
public class VectorSettings {
    private boolean enabled;
    private String ollamaUrl = "http://localhost:11434";
    private String model = "bge-m3";
    private String indexVersion = "bge-m3-1024-chunk600-v1";
    private String jdbcUrl = "jdbc:postgresql://localhost:5433/enterprise_knowledge";
    private String username = "knowledge";
    private String password = "knowledge_local_demo";
    @DecimalMin("0.0") @DecimalMax("1.0")
    private double minScore = 0.45;

    public String modelVersion() { return model + ":" + indexVersion; }
}
