package org.itheima.ai;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AiCredentialMapper {
    @Select("SELECT id FROM user WHERE id=#{id} AND " + DocumentKnowledgeMapper.ACTIVE_USER)
    Integer activeUser(int id);

    @Select("SELECT encrypted_key FROM ai_user_credential WHERE user_id=#{id}")
    String read(int id);

    @Insert("INSERT INTO ai_user_credential(user_id,encrypted_key) VALUES(#{id},#{encrypted}) "
            + "ON DUPLICATE KEY UPDATE encrypted_key=#{encrypted},updated_at=CURRENT_TIMESTAMP")
    void save(@Param("id") int id, @Param("encrypted") String encrypted);

    @Delete("DELETE FROM ai_user_credential WHERE user_id=#{id}")
    void remove(int id);
}
