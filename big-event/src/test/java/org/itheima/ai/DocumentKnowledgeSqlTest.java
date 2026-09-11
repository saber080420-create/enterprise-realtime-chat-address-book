package org.itheima.ai;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentKnowledgeSqlTest {
    @Test void concurrentUploadsCannotExceedQuotaWithinTransactions() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        try (var c = ds.getConnection(); var sql = c.createStatement()) {
            sql.execute("CREATE TABLE user(id INT PRIMARY KEY, role VARCHAR(30),status VARCHAR(20))");
            sql.execute("INSERT INTO user VALUES (1,'employee','active')");
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/knowledge/schema.sql"));
            for (int i = 0; i < 49; i++) sql.execute("INSERT INTO ai_document(owner_id,title,content,content_hash) VALUES(1,'demo','test','hash')");
        }
        var config = new Configuration(new Environment("test", new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), ds));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(DocumentKnowledgeMapper.class);
        var template = new org.mybatis.spring.SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        var service = new DocumentKnowledgeService(template.getMapper(DocumentKnowledgeMapper.class));
        var tx = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.Callable<Integer> upload = () -> {
            ready.countDown(); assertTrue(ready.await(3, java.util.concurrent.TimeUnit.SECONDS));
            try {
                tx.execute(status -> service.upload(1, new org.springframework.mock.web.MockMultipartFile("file", "a.txt", "text/plain", "demo".getBytes())));
                return 200;
            } catch (AiChatController.AiRequestException e) { return e.status; }
        };
        try {
            var first = pool.submit(upload); var second = pool.submit(upload);
            assertEquals(Set.of(200, 409), Set.of(first.get(8, java.util.concurrent.TimeUnit.SECONDS), second.get(8, java.util.concurrent.TimeUnit.SECONDS)));
            assertEquals(50, template.getMapper(DocumentKnowledgeMapper.class).count(1));
        } finally { pool.shutdownNow(); }
    }

    @Test void actualSchemaAndSqlEnforcePrivateOwnershipActiveUsersAndSoftRemoval() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        try (var c = ds.getConnection(); var sql = c.createStatement()) {
            sql.execute("CREATE TABLE user(id INT PRIMARY KEY, role VARCHAR(30),status VARCHAR(20))");
            sql.execute("INSERT INTO user VALUES (1,'employee','active'),(2,'employee','active'),(3,'admin','active'),(4,'employee','inactive')");
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/knowledge/schema.sql"));
        }
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(DocumentKnowledgeMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            var mapper = session.getMapper(DocumentKnowledgeMapper.class);
            assertEquals(1, mapper.lockOwner(1)); assertNull(mapper.lockOwner(4)); assertNull(mapper.lockOwner(999));
            var document = new KnowledgeDocument(); document.setOwnerId(1); document.setTitle("年假.txt");
            document.setContent("年假三天前申请"); document.setContentHash("a".repeat(64));
            mapper.insert(document); session.commit();
            int id = document.getId();
            assertEquals(1, mapper.count(1)); assertEquals("年假.txt", mapper.source(1, id).getTitle());
            for (int user : List.of(2, 3, 4, 999)) {
                assertTrue(mapper.candidates(user).isEmpty()); assertNull(mapper.source(user, id));
                assertEquals(0, mapper.remove(user, id));
            }
            try (var c = ds.getConnection(); var sql = c.createStatement()) { sql.execute("UPDATE user SET status='inactive' WHERE id=1"); }
            session.clearCache();
            assertNull(mapper.source(1, id)); assertEquals(0, mapper.remove(1, id));
            try (var c = ds.getConnection(); var sql = c.createStatement()) { sql.execute("UPDATE user SET status='active' WHERE id=1"); }
            session.clearCache();
            assertEquals(1, mapper.remove(1, id));
            assertNull(mapper.source(1, id)); assertTrue(mapper.candidates(1).isEmpty()); assertEquals(0, mapper.count(1));
            session.commit();
            try (var c = ds.getConnection(); var sql = c.createStatement(); var rows = sql.executeQuery("SELECT status,content FROM ai_document WHERE id=" + id)) {
                assertTrue(rows.next()); assertEquals("removed", rows.getString(1)); assertEquals("年假三天前申请", rows.getString(2));
            }
        }
    }
}
