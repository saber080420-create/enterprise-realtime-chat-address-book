package org.itheima.ai;

import lombok.Data;

@Data
public class KnowledgeDocument {
    private Integer id;
    private Integer ownerId;
    private String title;
    private String content;
    private String contentHash;
}
