package org.itheima.ai;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DocumentKnowledgeMapper {
    String ACTIVE_USER = "status='active' AND role IN ('employee','department_admin','system_admin','admin')";
    String VISIBLE = " FROM ai_document d JOIN user u ON u.id=#{userId} AND u.id=d.owner_id "
            + "WHERE u." + ACTIVE_USER + " AND d.status='active' ";

    // Serializes uploads per owner even across application instances; called inside a transaction.
    @Select("SELECT id FROM user WHERE id=#{userId} AND " + ACTIVE_USER + " FOR UPDATE")
    Integer lockOwner(int userId);

    @Select("SELECT count(*) FROM ai_document WHERE owner_id=#{userId} AND status='active'")
    int count(int userId);

    @Select("SELECT d.id,d.owner_id,d.title,d.content,d.content_hash" + VISIBLE + "ORDER BY d.id DESC LIMIT 51")
    List<KnowledgeDocument> candidates(@Param("userId") int userId);

    @Select("SELECT d.id,d.owner_id,d.title,d.content,d.content_hash" + VISIBLE + "AND d.id=#{id}")
    KnowledgeDocument source(@Param("userId") int userId, @Param("id") int id);

    @Insert("INSERT INTO ai_document(owner_id,title,content,content_hash) VALUES(#{ownerId},#{title},#{content},#{contentHash})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeDocument document);

    @Update("UPDATE ai_document SET status='removed' WHERE id=#{id} AND owner_id=#{userId} AND status='active' "
            + "AND EXISTS(SELECT 1 FROM user WHERE id=#{userId} AND " + ACTIVE_USER + ")")
    int remove(@Param("userId") int userId, @Param("id") int id);
}
