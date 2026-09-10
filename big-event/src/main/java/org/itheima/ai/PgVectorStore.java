package org.itheima.ai;

import org.springframework.stereotype.Component;
import java.sql.*;
import java.util.*;

/** Uses a separate JDBC connection, leaving Spring/MyBatis's primary MySQL datasource unchanged. */
@Component
public class PgVectorStore {
    private final VectorSettings settings;
    enum Resource { ANNOUNCEMENT, DOCUMENT }
    private final Resource resource;
    @org.springframework.beans.factory.annotation.Autowired
    public PgVectorStore(VectorSettings settings) { this(settings, Resource.ANNOUNCEMENT); }
    PgVectorStore(VectorSettings settings, Resource resource) { this.settings = settings; this.resource = resource; }
    // Only a server-owned enum chooses identifiers; callers cannot supply SQL/table names.
    private String sql(String template) {
        return resource == Resource.DOCUMENT
                ? template.replace("announcement_embedding", "document_embedding").replace("announcement_id", "document_id") : template;
    }
    private String version() { return settings.modelVersion() + (resource == Resource.DOCUMENT ? ":document-plain-v1" : ""); }
    public record Candidate(int id, String fingerprint) {}
    public record Match(int id, String fingerprint, int chunkIndex, double score) {}

    private Connection connect() throws SQLException {
        var properties = new Properties();
        properties.setProperty("user", settings.getUsername());
        properties.setProperty("password", settings.getPassword());
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "15");
        return DriverManager.getConnection(settings.getJdbcUrl(), properties);
    }

    public boolean current(int id, String fingerprint, int chunks) throws SQLException {
        try (var c = connect(); var q = c.prepareStatement(sql("SELECT count(*) FROM announcement_embedding WHERE announcement_id=? AND model_version=? AND fingerprint=?"))) {
            q.setInt(1, id); q.setString(2, version()); q.setString(3, fingerprint);
            try (var rows = q.executeQuery()) { rows.next(); return rows.getInt(1) == chunks; }
        }
    }

    public void replace(int id, String fingerprint, List<float[]> vectors) throws SQLException {
        // Serialize and validate before deleting anything. A failed transaction keeps the previous index.
        List<String> encoded = vectors.stream().map(PgVectorStore::vector).toList();
        try (var c = connect()) {
            c.setAutoCommit(false);
            try {
                try (var delete = c.prepareStatement(sql("DELETE FROM announcement_embedding WHERE announcement_id=? AND model_version=?"))) {
                    delete.setInt(1, id); delete.setString(2, version()); delete.executeUpdate();
                }
                try (var insert = c.prepareStatement(sql("INSERT INTO announcement_embedding(announcement_id,model_version,fingerprint,chunk_index,embedding) VALUES(?,?,?,?,?::vector)"))) {
                    for (int i = 0; i < encoded.size(); i++) {
                        insert.setInt(1, id); insert.setString(2, version()); insert.setString(3, fingerprint);
                        insert.setInt(4, i); insert.setString(5, encoded.get(i)); insert.addBatch();
                    }
                    insert.executeBatch();
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        }
    }

    public List<Match> search(List<Candidate> allowed, float[] query) throws SQLException {
        if (allowed.isEmpty()) return List.of();
        if (allowed.size() > 200) throw new IllegalArgumentException("检索候选超过 200 篇");
        String values = String.join(",", Collections.nCopies(allowed.size(), "(?::integer,?::varchar)"));
        String sql = "SELECT e.announcement_id,e.fingerprint,e.chunk_index,1-(e.embedding <=> ?::vector) AS score "
                + "FROM announcement_embedding e JOIN (VALUES " + values + ") AS allowed(id,hash) "
                + "ON e.announcement_id=allowed.id AND e.fingerprint=allowed.hash WHERE e.model_version=? "
                + "ORDER BY score DESC,e.announcement_id,e.chunk_index LIMIT 40";
        try (var c = connect(); var q = c.prepareStatement(sql(sql))) {
            int p = 1; q.setString(p++, vector(query));
            for (var a : allowed) { q.setInt(p++, a.id()); q.setString(p++, a.fingerprint()); }
            q.setString(p, version());
            List<Match> result = new ArrayList<>();
            try (var rows = q.executeQuery()) {
                while (rows.next()) result.add(new Match(rows.getInt(1), rows.getString(2), rows.getInt(3), rows.getDouble(4)));
            }
            return result;
        }
    }

    public Map<Integer, Integer> coverage(List<Candidate> allowed) throws SQLException {
        if (allowed.isEmpty()) return Map.of();
        if (allowed.size() > 200) throw new IllegalArgumentException("候选过多");
        String values = String.join(",", Collections.nCopies(allowed.size(), "(?::integer,?::varchar)"));
        String sql = "SELECT e.announcement_id,count(*) FROM announcement_embedding e JOIN (VALUES " + values
                + ") AS allowed(id,hash) ON e.announcement_id=allowed.id AND e.fingerprint=allowed.hash "
                + "WHERE e.model_version=? GROUP BY e.announcement_id";
        try (var c = connect(); var q = c.prepareStatement(sql(sql))) {
            int p = 1;
            for (var a : allowed) { q.setInt(p++, a.id()); q.setString(p++, a.fingerprint()); }
            q.setString(p, version());
            Map<Integer, Integer> result = new HashMap<>();
            try (var rows = q.executeQuery()) { while (rows.next()) result.put(rows.getInt(1), rows.getInt(2)); }
            return result;
        }
    }

    static String vector(float[] v) {
        if (v.length != OllamaEmbeddings.DIMENSIONS) throw new IllegalArgumentException("向量维度不匹配");
        StringJoiner result = new StringJoiner(",", "[", "]");
        double norm = 0;
        for (float f : v) {
            if (!Float.isFinite(f)) throw new IllegalArgumentException("非法向量");
            norm += (double) f * f; result.add(Float.toString(f));
        }
        if (norm == 0) throw new IllegalArgumentException("零向量");
        return result.toString();
    }
}
