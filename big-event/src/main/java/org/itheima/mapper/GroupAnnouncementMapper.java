package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.GroupAnnouncement;

import java.util.List;

@Mapper
public interface GroupAnnouncementMapper {

    @Insert("INSERT INTO group_announcement(group_id, publisher_id, title, content, publish_time, create_time, update_time) " +
            "VALUES(#{groupId}, #{publisherId}, #{title}, #{content}, NOW(), NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GroupAnnouncement ga);

    @Select("SELECT ga.*, u.nickname AS publisherName, u.user_pic AS publisherAvatar FROM group_announcement ga " +
            "LEFT JOIN user u ON u.id = ga.publisher_id WHERE ga.group_id = #{groupId} AND ga.status <> 'withdrawn' ORDER BY ga.publish_time DESC LIMIT #{limit}")
    List<java.util.Map<String, Object>> findLatestByGroupId(@Param("groupId") Integer groupId, @Param("limit") Integer limit);

    @Select("SELECT * FROM group_announcement WHERE id=#{id}")
    GroupAnnouncement findById(@Param("id") Integer id);

    @Update("UPDATE group_announcement SET status='withdrawn', update_time=NOW() WHERE id=#{id}")
    int withdraw(@Param("id") Integer id);

    @Insert("INSERT IGNORE INTO group_announcement_read(announcement_id, user_id, read_time, create_time) VALUES(#{announcementId}, #{userId}, NOW(), NOW())")
    int markRead(@Param("announcementId") Integer announcementId, @Param("userId") Integer userId);

    @Select("SELECT u.id, COALESCE(u.nickname, u.username) AS name, u.user_pic AS avatar FROM group_announcement_read r JOIN user u ON u.id=r.user_id WHERE r.announcement_id=#{announcementId} ORDER BY r.read_time DESC")
    List<java.util.Map<String, Object>> listReaders(@Param("announcementId") Integer announcementId);

    @Select("SELECT COUNT(*) FROM group_announcement_read WHERE announcement_id=#{announcementId}")
    Long countReaders(@Param("announcementId") Integer announcementId);

    @Select("SELECT COUNT(*) FROM group_announcement ga WHERE ga.group_id=#{groupId} AND ga.status='published' AND NOT EXISTS (SELECT 1 FROM group_announcement_read r WHERE r.announcement_id=ga.id AND r.user_id=#{userId})")
    Long countUnread(@Param("groupId") Integer groupId, @Param("userId") Integer userId);
}


