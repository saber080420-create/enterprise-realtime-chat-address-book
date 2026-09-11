package org.itheima.ai;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupSummarySqlTest {
    @Test void onlyCurrentMemberOwnUnrecalledTextCopiesAfterJoinAreVisible() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        try(var c=ds.getConnection(); var sql=c.createStatement()) {
            sql.execute("CREATE TABLE user(id INT PRIMARY KEY,role VARCHAR(30),status VARCHAR(20))");
            sql.execute("CREATE TABLE chat_group(id INT PRIMARY KEY,group_name VARCHAR(100))");
            sql.execute("CREATE TABLE chat_group_member(group_id INT,user_id INT,join_time TIMESTAMP)");
            sql.execute("CREATE TABLE chat_message(id BIGINT PRIMARY KEY,sender_id INT,receiver_id INT,group_id INT,chat_type VARCHAR(20),message_type VARCHAR(20),is_recalled BOOLEAN,content VARCHAR(2200),create_time TIMESTAMP,content_version INT)");
            sql.execute("INSERT INTO user VALUES(1,'employee','active'),(2,'admin','active'),(3,'employee','inactive')");
            sql.execute("INSERT INTO chat_group VALUES(5,'测试群')");
            sql.execute("INSERT INTO chat_group_member VALUES(5,1,'2026-09-08 09:00:00'),(5,3,'2026-09-08 09:00:00')");
            sql.execute("INSERT INTO chat_message VALUES "
                    + "(1,2,1,5,'group','text',false,'可见','2026-09-08 10:00:00',1),"
                    + "(2,2,2,5,'group','text',false,'他人副本','2026-09-08 10:00:00',1),"
                    + "(3,2,1,5,'group','text',true,'撤回','2026-09-08 10:00:00',1),"
                    + "(4,2,1,5,'group','file',false,'附件','2026-09-08 10:00:00',1),"
                    + "(5,2,1,5,'single','text',false,'私聊','2026-09-08 10:00:00',1),"
                    + "(6,2,1,5,'group','text',false,'入群前','2026-09-08 08:00:00',1),"
                    + "(7,2,1,5,'group','text',false,'结束边界','2026-09-09 00:00:00',1)");
            sql.execute("ALTER TABLE chat_message ADD is_deleted BOOLEAN DEFAULT false");
            sql.execute("INSERT INTO chat_message(id,sender_id,receiver_id,group_id,chat_type,message_type,is_recalled,content,create_time,content_version,is_deleted) VALUES(8,2,1,5,'group','text',false,'软删除','2026-09-08 10:00:00',1,true)");
        }
        var config = new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(GroupSummaryMapper.class);
        try(var session=new SqlSessionFactoryBuilder().build(config).openSession()) {
            var mapper=session.getMapper(GroupSummaryMapper.class);
            assertEquals(1,mapper.groups(1).size()); assertTrue(mapper.groups(2).isEmpty()); assertTrue(mapper.groups(3).isEmpty());
            var rows=mapper.messages(1,5,LocalDateTime.parse("2026-09-08T00:00:00"),LocalDateTime.parse("2026-09-09T00:00:00"));
            assertEquals(List.of(1L),rows.stream().map(r->r.getId()).toList());
            for(long id:List.of(2L,3L,4L,5L,6L,8L)) assertNull(mapper.source(1,5,id));
            assertNull(mapper.source(2,5,1L)); assertNull(mapper.source(1,99,1L));
            try(var c=ds.getConnection();var sql=c.createStatement()){sql.execute("DELETE FROM chat_group_member WHERE user_id=1");}
            session.clearCache(); assertNull(mapper.group(1,5)); assertNull(mapper.source(1,5,1L));
        }
    }
}
