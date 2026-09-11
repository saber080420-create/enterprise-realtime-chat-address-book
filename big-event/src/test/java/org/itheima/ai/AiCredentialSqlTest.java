package org.itheima.ai;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AiCredentialSqlTest {
    @Test void schemaAndMapperKeepOwnersIsolatedAndReplaceDeleteOnlyOwnRow() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        try (var c = ds.getConnection(); var sql = c.createStatement()) {
            sql.execute("CREATE TABLE user(id INT PRIMARY KEY,role VARCHAR(30),status VARCHAR(20))");
            sql.execute("INSERT INTO user VALUES(1,'employee','active'),(2,'admin','active'),(3,'employee','inactive')");
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/knowledge/byok.sql"));
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/knowledge/byok.sql"));
        }
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.addMapper(AiCredentialMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            var repo = session.getMapper(AiCredentialMapper.class);
            assertEquals(1, repo.activeUser(1)); assertNull(repo.activeUser(3)); assertNull(repo.activeUser(999));
            repo.save(1, "encrypted-a"); repo.save(2, "encrypted-b"); repo.save(1, "encrypted-c");
            assertEquals("encrypted-c", repo.read(1)); assertEquals("encrypted-b", repo.read(2));
            repo.remove(1); assertNull(repo.read(1)); assertEquals("encrypted-b", repo.read(2));
        }
    }
}
