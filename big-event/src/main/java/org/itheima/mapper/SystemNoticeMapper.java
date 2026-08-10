package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.SystemNotice;
import java.util.List;

@Mapper
public interface SystemNoticeMapper {

    @Insert("insert into system_notice(user_id, operator_id, type, title, content, is_read, read_time, create_time, update_time) " +
            "values(#{userId}, #{operatorId}, #{type}, #{title}, #{content}, #{isRead}, #{readTime}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(SystemNotice notice);

    @Select("select * from system_notice where user_id = #{userId} order by create_time desc limit #{limit} offset #{offset}")
    List<SystemNotice> findByUserId(Integer userId, Integer limit, Integer offset);

    @Select("select count(*) from system_notice where user_id = #{userId} and is_read = false")
    Integer countUnread(Integer userId);

    @Update("update system_notice set is_read = true, read_time = now(), update_time = now() where id = #{id}")
    void markRead(Integer id);

    @Update("update system_notice set is_read = true, read_time = now(), update_time = now() where user_id = #{userId} and is_read = false")
    void markAllRead(Integer userId);

    @Delete("delete from system_notice where id = #{id} and is_read = true")
    int deleteIfRead(Integer id);
}


