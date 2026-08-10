package org.itheima.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.itheima.pojo.User;

import java.util.List;

@Mapper
public interface UserMapper {
//根据ID查询用户
    @Select("select * from user where id=#{id}")
    User findById(Integer id);
    
//根据用户名查询用户
    @Select("select * from user where username=#{username}")
    User findByUserName(String username);
//添加用户
    @Options(useGeneratedKeys = true,keyProperty = "id")
    @Insert("insert into user(username,password,department_id,role,create_time,update_time)"+
        " values(#{username},#{password},#{departmentId},#{role},now(),now())")
    void add(User user);

    @Update("update user set nickname=#{nickname},realname=#{realname},email=#{email},update_time=#{updateTime} where id =#{id}")
    void update(User user);

    @Update("update user set user_pic=#{userPic},update_time=now() where id=#{id}")
    void updateAvatar(String userPic,Integer id);

    @Update("update user set password=#{md5String},update_time=now() where id =#{id}")
    void updatePwd(String md5String, Integer id);
    
    @Update("update user set realname=#{realname},update_time=now() where id =#{id}")
    void updateRealname(String realname, Integer id);
    
    @Update("update user set nickname=#{nickname},update_time=now() where id =#{id}")
    void updateNickname(String nickname, Integer id);
    
    @Update("update user set email=#{email},update_time=now() where id =#{id}")
    void updateEmail(String email, Integer id);
    
    @Update("update user set phone=#{phone},update_time=now() where id =#{id}")
    void updatePhone(String phone, Integer id);
    
    /**
     * 更新用户角色
     * 
     * @param userId 用户ID
     * @param role 角色
     */
    @Update("update user set role=#{role},update_time=now() where id=#{userId}")
    void updateRole(Integer userId, String role);
    
    /**
     * 更新用户状态（启用/禁用）
     * 
     * @param userId 用户ID
     * @param status 状态（active, inactive）
     */
    @Update("update user set status=#{status},update_time=now() where id=#{userId}")
    void updateStatus(Integer userId, String status);
    
    /**
     * 更新用户部门
     * 
     * @param userId 用户ID
     * @param departmentId 部门ID
     */
    @Update("update user set department_id=#{departmentId},update_time=now() where id=#{userId}")
    void updateDepartment(Integer userId, Integer departmentId);
    
    /**
     * 更新用户职位
     * 
     * @param userId 用户ID
     * @param position 职位
     */
    @Update("update user set position=#{position},update_time=now() where id=#{userId}")
    void updatePosition(Integer userId, String position);
    
    /**
     * 删除用户
     * 
     * @param userId 用户ID
     */
    @Delete("delete from user where id=#{userId}")
    void deleteUser(Integer userId);

    /**
     * 根据部门ID查询用户列表
     * 
     * @param departmentId 部门ID
     * @return 用户列表
     */
    @Select("select * from user where department_id=#{departmentId}")
    List<User> findByDepartmentId(Integer departmentId);
    
    /**
     * 查询所有用户
     * 
     * @return 所有用户列表
     */
    @Select("select * from user order by id asc")
    List<User> findAll();

    /**
     * 基于关键词搜索用户（支持用户名、真实姓名、昵称、电话、邮箱模糊匹配）
     * 注意：该方法仅用于检索匹配的少量结果，需配合 limit 限制返回数量。
     * 
     * @param keyword 关键词（至少2个字符）
     * @param limit 返回记录上限
     * @return 匹配的用户列表
     */
    @Select({
        "<script>",
        "SELECT * FROM user ",
        "WHERE (username LIKE CONCAT('%', #{keyword}, '%') ",
        "OR realname LIKE CONCAT('%', #{keyword}, '%') ",
        "OR nickname LIKE CONCAT('%', #{keyword}, '%') ",
        "OR phone LIKE CONCAT('%', #{keyword}, '%') ",
        "OR email LIKE CONCAT('%', #{keyword}, '%')) ",
        "ORDER BY id ASC ",
        "LIMIT #{limit}",
        "</script>"
    })
    List<User> searchByKeyword(String keyword, Integer limit);
}
