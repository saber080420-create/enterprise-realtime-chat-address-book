package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.Announcement;
import org.itheima.pojo.dto.AnnouncementDTO;

import java.util.List;
import java.util.Map;

/**
 * 公告Mapper接口
 */
@Mapper
public interface AnnouncementMapper {
    
    /**
     * 根据ID查询公告
     * 
     * @param id 公告ID
     * @return 公告信息
     */
    @Select("select * from announcement where id = #{id}")
    Announcement findById(Integer id);
    
    /**
     * 查询所有公告
     * 
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    @Select("select * from announcement order by publish_time desc limit #{limit} offset #{offset}")
    List<Announcement> findAll(Integer limit, Integer offset);
    
    /**
     * 查询特定部门的公告
     * 
     * @param departmentId 部门ID
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    @Select("select * from announcement where department_id = #{departmentId} or department_id is null order by publish_time desc limit #{limit} offset #{offset}")
    List<Announcement> findByDepartmentId(Integer departmentId, Integer limit, Integer offset);
    
    /**
     * 查询特定类型的公告
     * 
     * @param type 公告类型
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    @Select("select * from announcement where type = #{type} order by publish_time desc limit #{limit} offset #{offset}")
    List<Announcement> findByType(String type, Integer limit, Integer offset);
    
    /**
     * 查询特定发布者的公告
     * 
     * @param publisherId 发布者ID
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    @Select("select * from announcement where publisher_id = #{publisherId} order by publish_time desc limit #{limit} offset #{offset}")
    List<Announcement> findByPublisherId(Integer publisherId, Integer limit, Integer offset);
    
    /**
     * 添加公告
     * 
     * @param announcement 公告信息
     */
    @Insert("insert into announcement(title, content, publisher_id, type, department_id, status, publish_time, create_time, update_time) " +
            "values(#{title}, #{content}, #{publisherId}, #{type}, #{departmentId}, #{status}, #{publishTime}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(Announcement announcement);
    
    /**
     * 更新公告
     * 
     * @param announcement 公告信息
     */
    @Update("update announcement set title = #{title}, content = #{content}, publisher_id = #{publisherId}, type = #{type}, " +
            "department_id = #{departmentId}, status = #{status}, publish_time = #{publishTime}, update_time = now() where id = #{id}")
    void update(Announcement announcement);
    
    /**
     * 删除公告
     * 
     * @param id 公告ID
     */
    @Delete("delete from announcement where id = #{id}")
    void deleteById(Integer id);
    
    /**
     * 更新公告状态
     * 
     * @param id 公告ID
     * @param status 状态
     */
    @Update("update announcement set status = #{status}, update_time = now() where id = #{id}")
    void updateStatus(Integer id, String status);
    
    /**
     * 查询用户可见的公告
     * 
     * @param userId 用户ID
     * @param departmentIds 用户部门ID列表
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    @Select({"<script>",
            "select * from announcement where status = 'published' and ",
            "(department_id is null or department_id in ",
            "<foreach collection=\"departmentIds\" item=\"deptId\" open=\"(\" separator=\",\" close=\")\">",
            "#{deptId}",
            "</foreach>)",
            " order by publish_time desc limit #{limit} offset #{offset}",
            "</script>"})
    List<Announcement> findVisibleToUser(Integer userId, List<Integer> departmentIds, Integer limit, Integer offset);
    
    /**
     * 查询公告及其阅读状态
     * 
     * @param userId 用户ID
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告及阅读状态列表
     */
    @Select("SELECT a.*, ars.is_read, ars.read_time, " +
            "(SELECT COUNT(*) FROM announcement_read_status WHERE announcement_id = a.id AND is_read = true) AS read_count, " +
            "(SELECT COUNT(*) FROM user WHERE department_id = a.department_id OR a.department_id IS NULL) AS total_count, " +
            "u.username AS publisher_name, u.user_pic AS publisher_avatar, " +
            "d.name AS department_name " +
            "FROM announcement a " +
            "LEFT JOIN announcement_read_status ars ON a.id = ars.announcement_id AND ars.user_id = #{userId} " +
            "LEFT JOIN user u ON a.publisher_id = u.id " +
            "LEFT JOIN department d ON a.department_id = d.id " +
            "WHERE a.status = 'published' " +
            "ORDER BY a.publish_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> findWithReadStatus(Integer userId, Integer limit, Integer offset);
    
    /**
     * 统计公告阅读情况
     * 
     * @param id 公告ID
     * @return 阅读统计信息
     */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM announcement_read_status WHERE announcement_id = #{id} AND is_read = true) AS read_count, " +
            "(SELECT COUNT(*) FROM user WHERE department_id = " +
            "(SELECT department_id FROM announcement WHERE id = #{id}) OR " +
            "(SELECT department_id FROM announcement WHERE id = #{id}) IS NULL) AS total_count")
    AnnouncementDTO getReadStatistics(Integer id);
    
    /**
     * 搜索公告
     * 
     * @param keyword 关键字
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表
     */
    @Select("select * from announcement where title like concat('%', #{keyword}, '%') " +
            "or content like concat('%', #{keyword}, '%') " +
            "order by publish_time desc limit #{limit} offset #{offset}")
    List<Announcement> searchAnnouncements(String keyword, Integer limit, Integer offset);
    
    /**
     * 统计公告总数
     * 
     * @return 公告总数
     */
    @Select("select count(*) from announcement")
    Integer countAll();
    
    /**
     * 查询重要公告
     * 
     * @return 重要公告列表
     */
    @Select("select * from announcement where type = 'important' and status = 'published' order by publish_time desc")
    List<Announcement> findImportant();
    
    /**
     * 查询最新公告
     * 
     * @param limit 限制数量
     * @return 最新公告列表
     */
    @Select("select * from announcement where status = 'published' order by publish_time desc limit #{limit}")
    List<Announcement> findLatest(Integer limit);
    
    /**
     * 根据标题搜索公告
     * 
     * @param title 标题关键词
     * @return 公告列表
     */
    @Select("select * from announcement where title like concat('%', #{title}, '%') and status = 'published' order by publish_time desc")
    List<Announcement> findByTitle(String title);
    
    /**
     * 分页查询公告（支持按类型和部门ID筛选，含部门可见性逻辑）
     * 
     * @param type 公告类型（company或department）
     * @param departmentId 用户所在部门ID（用于可见性过滤）
     * @param limit 数量限制
     * @param offset 偏移量
     * @return 公告列表（包含发布者和部门信息）
     */
    @Select({"<script>",
            "select a.*, u.username as publisher_name, u.user_pic as publisher_avatar, d.department_name ",
            "from announcement a ",
            "left join user u on a.publisher_id = u.id ",
            "left join department d on a.department_id = d.id ",
            "where a.status = 'published' ",
            "and (a.department_id is null or ",
            "<if test=\"departmentId != null\">",
            "a.department_id = #{departmentId}",
            "</if>",
            "<if test=\"departmentId == null\">",
            "1 = 1",
            "</if>",
            ") ",
            "<if test=\"type != null and type == 'company'\">",
            "and a.department_id is null ",
            "</if>",
            "<if test=\"type != null and type == 'department' and departmentId != null\">",
            "and a.department_id = #{departmentId} ",
            "</if>",
            "order by a.publish_time desc limit #{limit} offset #{offset}",
            "</script>"})
    List<Announcement> findByScope(String type, Integer departmentId, Integer limit, Integer offset);
    
    /**
     * 分页查询公告（返回Map，包含联表详情字段）
     */
    @Select({"<script>",
            "select a.*, ",
            "u.username as publisherName, u.user_pic as publisherAvatar, ",
            "d.department_name as departmentName, ",
            "(SELECT COUNT(*) FROM announcement_read_status WHERE announcement_id = a.id AND is_read = true) AS readCount, ",
            "(SELECT COUNT(*) FROM user WHERE department_id = a.department_id OR a.department_id IS NULL) AS totalCount ",
            "from announcement a ",
            "left join user u on a.publisher_id = u.id ",
            "left join department d on a.department_id = d.id ",
            "where a.status = 'published' ",
            "and (a.department_id is null or ",
            "<if test=\"departmentId != null\">",
            "a.department_id = #{departmentId}",
            "</if>",
            "<if test=\"departmentId == null\">",
            "1 = 1",
            "</if>",
            ") ",
            "<if test=\"type != null and type == 'company'\">",
            "and a.department_id is null ",
            "</if>",
            "<if test=\"type != null and type == 'department' and departmentId != null\">",
            "and a.department_id = #{departmentId} ",
            "</if>",
            "order by a.publish_time desc limit #{limit} offset #{offset}",
            "</script>"})
    List<Map<String, Object>> findByScopeWithDetails(String type, Integer departmentId, Integer limit, Integer offset);
    
    /**
     * 统计公告总数（支持按类型和部门ID筛选，含部门可见性逻辑）
     * 
     * @param type 公告类型（company或department）
     * @param departmentId 用户所在部门ID（用于可见性过滤）
     * @return 公告总数
     */
    @Select({"<script>",
            "select count(*) from announcement where status = 'published' ",
            "and (department_id is null or ",
            "<if test=\"departmentId != null\">",
            "department_id = #{departmentId}",
            "</if>",
            "<if test=\"departmentId == null\">",
            "1 = 1",
            "</if>",
            ") ",
            "<if test=\"type != null and type == 'company'\">",
            "and department_id is null ",
            "</if>",
            "<if test=\"type != null and type == 'department' and departmentId != null\">",
            "and department_id = #{departmentId} ",
            "</if>",
            "</script>"})
    Integer countByScope(String type, Integer departmentId);
}