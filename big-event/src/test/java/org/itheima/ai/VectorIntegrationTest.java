package org.itheima.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real PGVector + local BGE-M3. Creates and removes only its own randomly named test schema. */
@EnabledIfSystemProperty(named = "vector.integration", matches = "true")
class VectorIntegrationTest {
    @Test void postgresRetrievalEnforcesHashAndPermissionCandidates() throws Exception {
        var settings = new VectorSettings();
        settings.setPassword(System.getenv().getOrDefault("PGVECTOR_PASSWORD", "knowledge_local_demo"));
        String schema = "codex_vector_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(settings.getJdbcUrl(), settings.getUsername(), settings.getPassword());
             var statement = c.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                statement.execute("SET search_path TO " + schema + ",public");
                try (var resource = getClass().getResourceAsStream("/db/vector/schema.sql")) {
                    String sql = new String(Objects.requireNonNull(resource).readAllBytes(), StandardCharsets.UTF_8);
                    for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part);
                }
                settings.setJdbcUrl(settings.getJdbcUrl() + "?currentSchema=" + schema + ",public");
                var store = new PgVectorStore(settings);
                var embeddings = new OllamaEmbeddings(new ObjectMapper(), settings);
                String vacation = "员工年度休假申请须提前五个工作日提交至人事部门审批。";
                String parking = "地下停车场将于星期六进行消防设备检修，当天禁止车辆驶入。";
                String query = "想歇几天还照常领薪水，要找谁同意？";
                List<float[]> vectors;
                if (Boolean.getBoolean("embedding.integration")) {
                    vectors = embeddings.embed(List.of(vacation, parking, query), new ChatModelGateway.Cancellation());
                } else {
                    float[] a = new float[1024]; a[0] = 1;
                    float[] b = new float[1024]; b[1] = 1;
                    vectors = List.of(a, b, a);
                }
                assertEquals(1024, vectors.get(0).length);
                store.replace(1, "revision1", List.of(vectors.get(0)));
                store.replace(2, "revision2", List.of(vectors.get(1)));
                assertTrue(store.current(1, "revision1", 1));
                assertThrows(SQLException.class, () -> store.replace(1, "x".repeat(65), List.of(vectors.get(1))));
                assertTrue(store.current(1, "revision1", 1)); // Failed replacement rolls back the deletion.
                settings.setIndexVersion("different-model-version");
                assertFalse(store.current(1, "revision1", 1));
                settings.setIndexVersion("bge-m3-1024-chunk600-v1");
                var allowed = List.of(new PgVectorStore.Candidate(1, "revision1"), new PgVectorStore.Candidate(2, "revision2"));
                assertEquals(Map.of(1, 1, 2, 1), store.coverage(allowed));
                var hits = store.search(allowed, vectors.get(2));
                assertEquals(1, hits.get(0).id());
                System.out.println("Real embeddings=" + Boolean.getBoolean("embedding.integration")
                        + "; synthetic documents: vacation=" + hits.get(0).score() + ", parking=" + hits.get(1).score());
                assertTrue(store.search(List.of(new PgVectorStore.Candidate(1, "stale")), vectors.get(2)).isEmpty());
                assertEquals(2, store.search(List.of(new PgVectorStore.Candidate(2, "revision2")), vectors.get(2)).get(0).id());
                store.replace(1, "updated", List.of(vectors.get(1)));
                assertFalse(store.current(1, "revision1", 1));
                assertTrue(store.current(1, "updated", 1));
                // Identical numeric IDs in different business resources must never collide.
                var docStore = new PgVectorStore(settings, PgVectorStore.Resource.DOCUMENT);
                assertFalse(docStore.current(1, "updated", 1));
                docStore.replace(1, "updated", List.of(vectors.get(0)));
                assertTrue(docStore.current(1, "updated", 1));
                assertEquals(1.0, docStore.search(List.of(new PgVectorStore.Candidate(1, "updated")), vectors.get(0)).get(0).score(), .00001);
                assertThrows(SQLException.class, () -> docStore.replace(1, "x".repeat(65), List.of(vectors.get(1))));
                assertTrue(docStore.current(1, "updated", 1));
                assertTrue(docStore.search(List.of(new PgVectorStore.Candidate(1, "stale")), vectors.get(0)).isEmpty());
                assertTrue(docStore.search(List.of(new PgVectorStore.Candidate(2, "revision2")), vectors.get(0)).isEmpty());
                assertTrue(store.current(1, "updated", 1));
                assertEquals(1.0, store.search(List.of(new PgVectorStore.Candidate(1, "updated")), vectors.get(1)).get(0).score(), .00001);
                settings.setIndexVersion("different-document-version");
                assertFalse(docStore.current(1, "updated", 1));
                settings.setIndexVersion("bge-m3-1024-chunk600-v1");
                if (Boolean.getBoolean("embedding.integration")) {
                    settings.setEnabled(true);
                    var document = new KnowledgeDocument(); document.setId(1); document.setOwnerId(7);
                    document.setTitle("休假说明.txt"); document.setContent(vacation); document.setContentHash("synthetic");
                    var mapper = org.mockito.Mockito.mock(DocumentKnowledgeMapper.class);
                    org.mockito.Mockito.when(mapper.candidates(7)).thenReturn(List.of(document));
                    org.mockito.Mockito.when(mapper.source(7, 1)).thenReturn(document);
                    var service = new DocumentVectorService(mapper, embeddings, docStore, settings);
                    try {
                        service.sync(7); awaitDocumentSync(service);
                        assertEquals(1, service.status(7).get("processed"));
                        assertEquals(0, service.status(7).get("reused"));
                        service.sync(7); awaitDocumentSync(service);
                        assertEquals(1, service.status(7).get("reused"));
                        var retrieved = service.retrieve(7, query, new ChatModelGateway.Cancellation());
                        assertEquals(1, retrieved.sources().size());
                        assertEquals("document", retrieved.sources().get(0).sourceType());
                        assertEquals(vacation, retrieved.sources().get(0).excerpt());
                        org.mockito.Mockito.when(mapper.source(7, 1)).thenReturn(null);
                        assertTrue(service.retrieve(7, query, new ChatModelGateway.Cancellation()).sources().isEmpty());
                        assertTrue(service.retrieve(8, query, new ChatModelGateway.Cancellation()).sources().isEmpty());
                    } finally { service.shutdown(); }
                }
            } finally {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private void awaitDocumentSync(DocumentVectorService service) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
        while (service.status(7).get("state").equals("running") && System.nanoTime() < deadline) Thread.sleep(20);
        assertEquals("completed", service.status(7).get("state"));
    }
}
