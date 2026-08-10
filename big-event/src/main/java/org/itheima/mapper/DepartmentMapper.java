package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.Department;

import java.util.List;

/**
 * 部门Mapper接口
 */
@Mapper
public interface DepartmentMapper {
    
    /**
     * 根据ID查询部门
     * 
     * @param id 部门ID
     * @return 部门信息
     */
    @Select("select * from department where id = #{id}")
    Department findById(Integer id);
    
    /**
     * 查询所有部门
     * 
     * @return 部门列表
     */
    @Select("select * from department order by create_time desc")
    List<Department> findAll();
    
    /**
     * 查询子部门
     * 
     * @param parentId 父部门ID
     * @return 子部门列表
     */
    @Select("select * from department where parent_id = #{parentId} order by create_time asc")
    List<Department> findByParentId(Integer parentId);
    
    /**
     * 添加部门
     * 
     * @param department 部门信息
     */
    @Insert("insert into department(department_name, parent_id, description, create_user_id, create_time, update_time) " +
            "values(#{departmentName}, #{parentId}, #{description}, #{createUserId}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(Department department);
    
    /**
     * 更新部门
     * 
     * @param department 部门信息
     */
    @Update("update department set department_name = #{departmentName}, parent_id = #{parentId}, " +
            "description = #{description}, update_time = now() where id = #{id}")
    void update(Department department);
    
    /**
     * 删除部门
     * 
     * @param id 部门ID
     */
    @Delete("delete from department where id = #{id}")
    void deleteById(Integer id);
    
    /**
     * 根据部门名称查询部门
     * 
     * @param departmentName 部门名称
     * @return 部门信息
     */
    @Select("select * from department where department_name = #{departmentName}")
    Department findByName(String departmentName);
    
    /**
     * 统计部门下的用户数量
     * 
     * @param departmentId 部门ID
     * @return 用户数量
     */
    @Select("select count(*) from `user` where department_id = #{departmentId}")
    Integer countUsersByDepartmentId(Integer departmentId);
    
    /**
     * 查询所有部门及其用户数量
     * 
     * @return 部门列表及用户数量
     */
    @Select("SELECT d.*, (SELECT COUNT(*) FROM `user` u WHERE u.department_id = d.id) AS user_count " +
            "FROM department d ORDER BY d.create_time DESC")
    List<Department> findAllWithUserCount();
}