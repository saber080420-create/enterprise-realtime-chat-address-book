package org.itheima.ai;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementKnowledgeSqlTest {
    @Test void realMapperSqlEnforcesVisibilityAndCurrentUserState() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        try (var connection = ds.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE user(id INT PRIMARY KEY, role VARCHAR(30), department_id INT, status VARCHAR(20))");
            sql.execute("CREATE TABLE announcement(id INT PRIMARY KEY,title VARCHAR(200),content VARCHAR(22000),type VARCHAR(30),department_id INT,status VARCHAR(20),publish_time TIMESTAMP,update_time TIMESTAMP)");
            sql.execute("INSERT INTO user VALUES (1,'employee',10,'active'),(2,'employee',20,'active'),(3,'employee',NULL,'active'),(4,'system_admin',NULL,'active'),(5,'department_admin',10,'active'),(6,'employee',10,'inactive')");
            sql.execute("INSERT INTO announcement(id,title,content,type,department_id,status) VALUES "
                    + "(1,'全员','公司资料','company',NULL,'published'),(2,'部门10','A部门资料','department',10,'published'),"
                    + "(3,'部门20','B部门资料','department',20,'published'),(4,'草稿','秘密草稿','company',NULL,'draft'),"
                    + "(5,'撤回','旧资料','company',NULL,'revoked'),(6,'非法部门空值','不能公开','department',NULL,'published')");
        }
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(AnnouncementKnowledgeMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            var mapper = session.getMapper(AnnouncementKnowledgeMapper.class);
            assertEquals(Set.of(1, 2), ids(mapper, 1));
            assertEquals(Set.of(1, 3), ids(mapper, 2));
            assertEquals(Set.of(1), ids(mapper, 3)); // A missing department does not mean all departments.
            assertEquals(Set.of(1, 2, 3), ids(mapper, 4));
            assertEquals(Set.of(1, 2), ids(mapper, 5));
            assertTrue(ids(mapper, 6).isEmpty());
            assertTrue(ids(mapper, 999).isEmpty());
            assertNull(mapper.visibleSource(1, 3));
            assertNull(mapper.visibleSource(4, 4));
            try (var connection = ds.getConnection(); var sql = connection.createStatement()) {
                sql.execute("UPDATE user SET department_id=20 WHERE id=1");
                sql.execute("UPDATE announcement SET status='revoked' WHERE id=3");
            }
            session.clearCache();
            assertNull(mapper.visibleSource(1, 2));
            assertNull(mapper.visibleSource(1, 3));
            assertEquals(Set.of(1), ids(mapper, 1));
        }
    }

    private Set<Integer> ids(AnnouncementKnowledgeMapper mapper, int user) {
        return new HashSet<>(mapper.candidates(user).stream().map(a -> a.getId()).toList());
    }
}
