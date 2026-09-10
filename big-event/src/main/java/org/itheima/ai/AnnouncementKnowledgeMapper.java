package org.itheima.ai;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.Announcement;
import java.util.List;

/** AI-specific reads: check the user's current database role/department on every query. */
@Mapper
public interface AnnouncementKnowledgeMapper {
    String VISIBLE = " FROM announcement a JOIN user u ON u.id = #{userId} "
            + "WHERE u.status = 'active' AND u.role IN ('employee','department_admin','system_admin','admin') "
            + "AND a.status = 'published' AND ("
            + "(a.type = 'company' AND a.department_id IS NULL) OR "
            + "(a.type = 'department' AND a.department_id IS NOT NULL AND "
            + "(a.department_id = u.department_id OR u.role IN ('system_admin','admin')))) ";

    @Select("SELECT a.id,a.title,LEFT(a.content,20000) AS content,a.type,a.department_id,a.status,a.update_time "
            + VISIBLE + "ORDER BY a.publish_time DESC, a.id DESC LIMIT 201")
    List<Announcement> candidates(@Param("userId") int userId);

    @Select("SELECT a.id,a.title,LEFT(a.content,20000) AS content,a.type,a.department_id,a.status,a.update_time "
            + VISIBLE + "AND a.id = #{id}")
    Announcement visibleSource(@Param("userId") int userId, @Param("id") int id);
}
