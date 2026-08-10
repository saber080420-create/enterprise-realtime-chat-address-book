package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.AddressBook;
import org.itheima.pojo.dto.AddressBookContactDTO;

import java.util.List;

/**
 * AddressBookMapper
 */
@Mapper
public interface AddressBookMapper {

    @Select("select * from address_book where user_id=#{userId} and contact_id=#{contactId} limit 1")
    AddressBook findByUserAndContact(@Param("userId") Integer userId, @Param("contactId") Integer contactId);

    @Insert("insert into address_book(user_id, contact_id, group_id, alias, tags, remark, create_time, update_time) values(#{userId}, #{contactId}, #{groupId}, #{alias}, #{tags}, #{remark}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AddressBook ab);

    @Select("select * from address_book where user_id=#{userId} order by id desc")
    List<AddressBook> listByUserId(Integer userId);

    /**
     * 查询用户的通讯录联系人列表（包含联系人详细信息和分组信息）
     * 说明：
     * - 原实现仅 INNER JOIN user 表，导致 external 联系人（contact_type='external'）被过滤。
     * - 现改为 UNION ALL：
     *   1) 内部联系人（user 表）
     *   2) 外部联系人（external_contact 表）
     * - 两部分都 LEFT JOIN 分组，并在最外层统一排序（按分组排序、创建时间倒序）。
     */
    @Select(
        "SELECT * FROM ( " +
        "  SELECT ab.id, ab.user_id, ab.contact_id, NULL AS external_id, 'internal' AS contact_type, ab.group_id, ab.alias, ab.tags, ab.remark, " +
        "         ab.create_time, ab.update_time, " +
        "         u.username AS contact_name, u.user_pic AS contact_avatar, u.position, " +
        "         d.department_name, ag.group_name, ua.online, " +
        "         (SELECT COUNT(*) FROM chat_message cm " +
        "          WHERE ((cm.sender_id = #{userId} AND cm.receiver_id = ab.contact_id) " +
        "                 OR (cm.sender_id = ab.contact_id AND cm.receiver_id = #{userId})) " +
        "            AND cm.is_read = false AND cm.sender_id = ab.contact_id) AS unread_count, " +
        "         ag.sort_order AS group_sort, u.phone, u.email " +
        "  FROM address_book ab " +
        "  JOIN user u ON ab.contact_id = u.id " +
        "  LEFT JOIN department d ON u.department_id = d.id " +
        "  LEFT JOIN address_group ag ON ab.group_id = ag.id " +
        "  LEFT JOIN (SELECT user_id, online FROM user_activity WHERE online = true GROUP BY user_id) ua " +
        "         ON ab.contact_id = ua.user_id " +
        "  WHERE ab.user_id = #{userId} AND (ab.contact_type IS NULL OR ab.contact_type = 'internal') " +
        "  UNION ALL " +
        "  SELECT ab.id, ab.user_id, ab.contact_id, ab.external_id, 'external' AS contact_type, ab.group_id, ab.alias, ab.tags, ab.remark, " +
        "         ab.create_time, ab.update_time, " +
        "         ec.name AS contact_name, NULL AS contact_avatar, ec.position, " +
        "         ec.company AS department_name, ag.group_name, NULL AS online, " +
        "         0 AS unread_count, " +
        "         ag.sort_order AS group_sort, ec.phone, ec.email " +
        "  FROM address_book ab " +
        "  JOIN external_contact ec ON ab.external_id = ec.id " +
        "  LEFT JOIN address_group ag ON ab.group_id = ag.id " +
        "  WHERE ab.user_id = #{userId} AND ab.contact_type = 'external' " +
        ") t " +
        "ORDER BY t.group_sort ASC, t.create_time DESC"
    )
    List<AddressBookContactDTO> listContactsWithDetailsByUserId(Integer userId);

    /**
     * 将当前用户某个分组下的联系人迁移到目标分组
     */
    @Update("update address_book set group_id=#{targetGroupId}, update_time=now() where user_id=#{userId} and group_id=#{sourceGroupId}")
    int moveContactsToGroup(@Param("userId") Integer userId, @Param("sourceGroupId") Integer sourceGroupId, @Param("targetGroupId") Integer targetGroupId);

    /**
     * 更新当前用户与指定联系人的通讯录记录（分组、别名、备注）
     * @param userId 当前用户ID
     * @param contactId 联系人ID
     * @param groupId 分组ID
     * @param alias 备注名
     * @param remark 备注
     * @return 受影响行数
     */
    @Update("update address_book set group_id=#{groupId}, alias=#{alias}, remark=#{remark}, update_time=now() where user_id=#{userId} and contact_id=#{contactId}")
    int updateByUserAndContact(@Param("userId") Integer userId,
                               @Param("contactId") Integer contactId,
                               @Param("groupId") Integer groupId,
                               @Param("alias") String alias,
                               @Param("remark") String remark);

    /**
     * 基于通讯录记录ID更新（适配外部联系人无 contact_id 的情况）
     * @param userId 当前用户ID
     * @param abId 通讯录记录ID
     * @param groupId 分组ID
     * @param alias 备注名
     * @param remark 备注
     */
    @Update("update address_book set group_id=#{groupId}, alias=#{alias}, remark=#{remark}, update_time=now() where id=#{abId} and user_id=#{userId}")
    int updateByUserAndAb(@Param("userId") Integer userId,
                          @Param("abId") Integer abId,
                          @Param("groupId") Integer groupId,
                          @Param("alias") String alias,
                          @Param("remark") String remark);

    /**
     * 删除当前用户与指定联系人的通讯录记录
     * @param userId 当前用户ID
     * @param contactId 联系人ID
     * @return 受影响行数
     */
    @Delete("delete from address_book where user_id=#{userId} and contact_id=#{contactId}")
    int deleteByUserAndContact(@Param("userId") Integer userId, @Param("contactId") Integer contactId);

    /**
     * 基于通讯录记录ID删除（适配外部联系人无 contact_id 的情况）
     * @param userId 当前用户ID
     * @param abId 通讯录记录ID
     */
    @Delete("delete from address_book where id=#{abId} and user_id=#{userId}")
    int deleteByUserAndAb(@Param("userId") Integer userId, @Param("abId") Integer abId);

    /**
     * 根据当前用户与外部联系人ID查询通讯录记录
     */
    @Select("select * from address_book where user_id=#{userId} and external_id=#{externalId} and contact_type='external' limit 1")
    AddressBook findByUserAndExternal(@Param("userId") Integer userId, @Param("externalId") Integer externalId);

    /**
     * 根据 abId 与 userId 查询通讯录记录
     * @param userId 当前用户ID
     * @param abId 通讯录记录ID
     * @return AddressBook 记录或 null
     */
    @Select("select * from address_book where id=#{abId} and user_id=#{userId} limit 1")
    AddressBook findByIdAndUser(@Param("userId") Integer userId, @Param("abId") Integer abId);

    /**
     * 插入外部联系人通讯录记录
     */
    @Insert("insert into address_book(user_id, contact_id, external_id, contact_type, group_id, alias, tags, remark, create_time, update_time) " +
            "values(#{userId}, NULL, #{externalId}, 'external', #{groupId}, #{alias}, #{tags}, #{remark}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertExternal(AddressBook ab);
}