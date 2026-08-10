package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.ExternalContact;

import java.util.List;

/**
 * ExternalContactMapper
 * 提供外部联系人的增删改查SQL操作
 */
@Mapper
public interface ExternalContactMapper {

    /**
     * 新增外部联系人
     */
    @Insert("INSERT INTO external_contact(name, phone, email, company, position, create_time, update_time) " +
            "VALUES(#{name}, #{phone}, #{email}, #{company}, #{position}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ExternalContact ec);

    /**
     * 根据ID查询外部联系人
     */
    @Select("SELECT id, name, phone, email, company, position, create_time, update_time FROM external_contact WHERE id=#{id}")
    ExternalContact findById(Integer id);

    /**
     * 根据手机号查询（用于去重）
     */
    @Select("SELECT id, name, phone, email, company, position, create_time, update_time FROM external_contact WHERE phone=#{phone} LIMIT 1")
    ExternalContact findByPhone(String phone);

    /**
     * 更新外部联系人
     */
    @Update("UPDATE external_contact SET name=#{name}, phone=#{phone}, email=#{email}, company=#{company}, position=#{position}, update_time=NOW() WHERE id=#{id}")
    int update(ExternalContact ec);

    /**
     * 删除外部联系人
     */
    @Delete("DELETE FROM external_contact WHERE id=#{id}")
    int delete(Integer id);

    /**
     * 根据关键字搜索外部联系人
     */
    @Select("SELECT id, name, phone, email, company, position, create_time, update_time FROM external_contact " +
            "WHERE name LIKE CONCAT('%',#{keyword},'%') OR phone LIKE CONCAT('%',#{keyword},'%') OR company LIKE CONCAT('%',#{keyword},'%') ORDER BY update_time DESC")
    List<ExternalContact> search(String keyword);
}