package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.AddressGroup;

import java.util.List;

/**
 * AddressGroupMapper
 * 负责通讯录分组的增删改查。
 */
@Mapper
public interface AddressGroupMapper {

    @Select("select * from address_group where user_id=#{userId} order by sort_order asc, id asc")
    List<AddressGroup> listByUserId(Integer userId);

    @Select("select * from address_group where user_id=#{userId} and is_default=1 limit 1")
    AddressGroup getDefaultGroup(Integer userId);

    @Insert("insert into address_group(user_id, group_name, is_default, sort_order, create_time, update_time) values(#{userId}, #{groupName}, #{isDefault}, #{sortOrder}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AddressGroup group);

    @Select("select * from address_group where id=#{id}")
    AddressGroup findById(Integer id);

    /**
     * 根据用户ID和分组名查询分组（用于分组名重复校验）
     */
    @Select("select * from address_group where user_id=#{userId} and group_name=#{groupName} limit 1")
    AddressGroup findByUserIdAndGroupName(@Param("userId") Integer userId, @Param("groupName") String groupName);

    /** 删除分组 */
    @Delete("delete from address_group where id=#{id}")
    int deleteById(Integer id);

    /**
     * 更新分组名称
     * @param id 分组ID
     * @param groupName 新分组名称
     * @return 受影响行数
     */
    @Update("update address_group set group_name=#{groupName}, update_time=now() where id=#{id}")
    int updateGroupName(@Param("id") Integer id, @Param("groupName") String groupName);
}