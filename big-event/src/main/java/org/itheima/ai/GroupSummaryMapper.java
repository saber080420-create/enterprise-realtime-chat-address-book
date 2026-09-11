package org.itheima.ai;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.ChatMessage;
import org.itheima.pojo.ChatGroup;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GroupSummaryMapper {
    String MEMBER = " FROM chat_group g JOIN chat_group_member gm ON gm.group_id=g.id "
            + "JOIN user u ON u.id=gm.user_id WHERE u.id=#{userId} AND u.status='active' "
            + "AND u.role IN ('employee','department_admin','system_admin','admin') ";
    String VISIBLE = " FROM chat_message m WHERE m.group_id=#{groupId} AND m.receiver_id=#{userId} "
            + "AND m.chat_type='group' AND m.message_type='text' AND COALESCE(m.is_recalled,false)=false AND m.is_deleted=false "
            + "AND EXISTS (SELECT 1" + MEMBER + "AND g.id=m.group_id AND m.create_time>=gm.join_time) ";
    @Select("SELECT DISTINCT g.id,g.group_name" + MEMBER + "ORDER BY g.id DESC LIMIT 100")
    List<ChatGroup> groups(@Param("userId") int userId);
    @Select("SELECT DISTINCT g.id,g.group_name" + MEMBER + "AND g.id=#{groupId}")
    ChatGroup group(@Param("userId") int userId, @Param("groupId") int groupId);
    // Fan-out storage: only this user's copy, never combine other recipients' copies.
    @Select("SELECT m.id,m.sender_id,LEFT(m.content,2001) content,m.create_time,m.content_version" + VISIBLE
            + "AND m.create_time>=#{start} AND m.create_time<#{end} ORDER BY m.create_time DESC,m.id DESC LIMIT 101")
    List<ChatMessage> messages(@Param("userId") int userId, @Param("groupId") int groupId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    @Select("SELECT m.id,m.sender_id,LEFT(m.content,2001) content,m.create_time,m.content_version" + VISIBLE + "AND m.id=#{id}")
    ChatMessage source(@Param("userId") int userId, @Param("groupId") int groupId, @Param("id") long id);
}
