package org.itheima.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * 系统管理员初始化类
 * 在应用启动时执行SQL脚本，确保系统中存在管理员用户
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        // 读取SQL脚本
        ClassPathResource resource = new ClassPathResource("init-admin.sql");
        String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        
        // 执行SQL脚本
        try {
            // 分割SQL语句（按分号分割）
            String[] sqlStatements = sql.split(";");
            for (String statement : sqlStatements) {
                if (!statement.trim().isEmpty()) {
                    jdbcTemplate.execute(statement);
                }
            }
            System.out.println("系统管理员初始化成功");
        } catch (Exception e) {
            System.err.println("系统管理员初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}