package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.AnnouncementReadStatus;

import java.util.List;

/**
 * 公告阅读状态Mapper接口
 */
@Mapper
public interface AnnouncementReadStatusMapper {
    
    /**
     * 根据ID查询阅读状态
     * 
     * @param id 阅读状态ID
     * @return 阅读状态信息
     */
    @Select("select * from announcement_read_status where id = #{id}")
    AnnouncementReadStatus findById(Integer id);
    
    /**
     * 查询公告的所有阅读状态
     * 
     * @param announcementId 公告ID
     * @return 阅读状态列表
     */
    @Select("select * from announcement_read_status where announcement_id = #{announcementId}")
    List<AnnouncementReadStatus> findByAnnouncementId(Integer announcementId);
    
    /**
     * 查询用户的所有阅读状态
     * 
     * @param userId 用户ID
     * @return 阅读状态列表
     */
    @Select("select * from announcement_read_status where user_id = #{userId}")
    List<AnnouncementReadStatus> findByUserId(Integer userId);
    
    /**
     * 查询特定用户对特定公告的阅读状态
     * 
     * @param announcementId 公告ID
     * @param userId 用户ID
     * @return 阅读状态信息
     */
    @Select("select * from announcement_read_status where announcement_id = #{announcementId} and user_id = #{userId}")
    AnnouncementReadStatus findByAnnouncementIdAndUserId(Integer announcementId, Integer userId);
    
    /**
     * 添加阅读状态
     * 
     * @param announcementReadStatus 阅读状态信息
     */
    @Insert("insert into announcement_read_status(announcement_id, user_id, is_read, read_time, create_time, update_time) " +
            "values(#{announcementId}, #{userId}, #{isRead}, #{readTime}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(AnnouncementReadStatus announcementReadStatus);
    
    /**
     * 更新阅读状态
     * 
     * @param announcementReadStatus 阅读状态信息
     */
    @Update("update announcement_read_status set is_read = #{isRead}, read_time = #{readTime}, " +
            "update_time = now() where id = #{id}")
    void update(AnnouncementReadStatus announcementReadStatus);
    
    /**
     * 删除阅读状态
     * 
     * @param id 阅读状态ID
     */
    @Delete("delete from announcement_read_status where id = #{id}")
    void deleteById(Integer id);
    
    /**
     * 删除公告的所有阅读状态
     * 
     * @param announcementId 公告ID
     */
    @Delete("delete from announcement_read_status where announcement_id = #{announcementId}")
    void deleteByAnnouncementId(Integer announcementId);
    
    /**
     * 删除用户的所有阅读状态
     * 
     * @param userId 用户ID
     */
    @Delete("delete from announcement_read_status where user_id = #{userId}")
    void deleteByUserId(Integer userId);
    
    /**
     * 将指定公告的已读状态重置为未读
     * 仅重置当前为已读的记录（is_read = true），并清空read_time
     * 
     * @param announcementId 公告ID
     */
    @Update("update announcement_read_status set is_read = false, read_time = null, update_time = now() where announcement_id = #{announcementId} and is_read = true")
    void resetReadStatusesByAnnouncementId(Integer announcementId);
    
    /**
     * 标记公告为已读
     * 
     * @param announcementId 公告ID
     * @param userId 用户ID
     */
    @Insert("insert into announcement_read_status(announcement_id, user_id, is_read, read_time, create_time, update_time) " +
            "values(#{announcementId}, #{userId}, true, now(), now(), now()) " +
            "on duplicate key update is_read = true, read_time = now(), update_time = now()")
    void markAsRead(Integer announcementId, Integer userId);
    
    /**
     * 批量标记公告为已读
     * 
     * @param announcementIds 公告ID列表
     * @param userId 用户ID
     * @return 更新的记录数
     */
    @Insert({"<script>",
            "insert into announcement_read_status(announcement_id, user_id, is_read, read_time, create_time, update_time) values ",
            "<foreach collection='announcementIds' item='announcementId' separator=','>",
            "(#{announcementId}, #{userId}, true, now(), now(), now())",
            "</foreach>",
            "on duplicate key update is_read = true, read_time = now(), update_time = now()",
            "</script>"})
    int batchMarkAsRead(List<Integer> announcementIds, Integer userId);
    
    /**
     * 统计公告已读人数
     * 
     * @param announcementId 公告ID
     * @return 已读人数
     */
    @Select("select count(*) from announcement_read_status where announcement_id = #{announcementId} and is_read = true")
    Integer countReadByAnnouncementId(Integer announcementId);
    
    /**
     * 统计用户未读公告数量
     * 
     * @param userId 用户ID
     * @return 未读公告数量
     */
    @Select("select count(*) from announcement a " +
            "left join announcement_read_status ars on a.id = ars.announcement_id and ars.user_id = #{userId} " +
            "where a.status = 'published' and (ars.is_read is null or ars.is_read = false)")
    Integer countUnreadByUserId(Integer userId);
    
    /**
     * 批量添加阅读状态
     * 
     * @param statusList 阅读状态列表
     * @return 添加的记录数
     */
    @Insert({"<script>",
            "insert into announcement_read_status(announcement_id, user_id, is_read, read_time, create_time, update_time) values ",
            "<foreach collection='list' item='status' separator=','>",
            "(#{status.announcementId}, #{status.userId}, #{status.isRead}, #{status.readTime}, #{status.createTime}, #{status.updateTime})",
            "</foreach>",
            "</script>"})
    int batchAdd(List<AnnouncementReadStatus> statusList);
    
    /**
     * 获取用户已读公告ID列表
     * 
     * @param userId 用户ID
     * @return 已读公告ID列表
     */
    @Select("select announcement_id from announcement_read_status where user_id = #{userId} and is_read = true")
    List<Integer> findReadAnnouncementIdsByUserId(Integer userId);
    
    /**
     * 获取用户未读公告ID列表
     * 
     * @param userId 用户ID
     * @return 未读公告ID列表
     */
    @Select("select a.id from announcement a " +
            "left join announcement_read_status ars on a.id = ars.announcement_id and ars.user_id = #{userId} " +
            "where a.status = 'published' and (ars.is_read is null or ars.is_read = false)")
    List<Integer> findUnreadAnnouncementIdsByUserId(Integer userId);
    
    /**
     * 查询指定公告的已读用户详情（含部门名称）
     * @param announcementId 公告ID
     * @return 用户详情列表
     */
    @Select("SELECT u.id, u.username, u.nickname as name, u.user_pic as avatar, d.department_name as departmentName " +
            "FROM announcement_read_status ars " +
            "JOIN user u ON ars.user_id = u.id " +
            "LEFT JOIN department d ON u.department_id = d.id " +
            "WHERE ars.announcement_id = #{announcementId} AND ars.is_read = true")
    List<java.util.Map<String, Object>> findReadUsersByAnnouncementId(Integer announcementId);
}