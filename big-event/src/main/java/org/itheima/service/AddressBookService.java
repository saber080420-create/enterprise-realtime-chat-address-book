package org.itheima.service;

import org.itheima.pojo.AddressBook;
import org.itheima.pojo.AddressGroup;
import org.itheima.pojo.dto.AddressBookContactDTO;
import org.itheima.pojo.ExternalContact;

import java.util.List;

/**
 * AddressBookService
 * 业务：将用户添加到个人通讯录（可指定分组），如未指定分组则落入默认分组。
 */
public interface AddressBookService {

    /**
     * 添加联系人到当前登录用户的通讯录
     * @param contactId 要添加的联系人用户ID
     * @param groupId 可选的分组ID（若为null则自动使用默认分组）
     * @param alias 可选备注名
     * @param remark 可选备注
     * @return 保存后的AddressBook记录
     */
    AddressBook addToMyAddressBook(Integer contactId, Integer groupId, String alias, String remark);

    /**
     * 添加外部联系人到当前登录用户的通讯录
     * 函数级注释：
     * - 若传入手机号已存在于外部联系人库，则直接复用该记录，避免重复创建
     * - 若未指定分组，则自动创建或复用“默认分组”
     * - 在 address_book 中以 external_id + contact_type='external' 建立关系
     *
     * @param externalContact 外部联系人基础信息（姓名、电话、邮箱、公司、职位）
     * @param groupId 可选分组ID
     * @param alias 可选备注名
     * @param remark 可选备注
     * @return 保存后的AddressBook记录
     */
    AddressBook addExternalContact(ExternalContact externalContact, Integer groupId, String alias, String remark);

    /**
     * 编辑当前登录用户的通讯录联系人（支持修改分组、备注名、备注）——按联系人用户ID
     * @param contactId 联系人用户ID
     * @param groupId 新分组ID（可为null表示不改动或后续按业务处理）
     * @param alias 新备注名（可为null则不改动）
     * @param remark 新备注（可为null则不改动）
     */
    void editMyContact(Integer contactId, Integer groupId, String alias, String remark);

    /**
     * 删除当前登录用户通讯录中的某个联系人——按联系人用户ID
     * @param contactId 联系人用户ID
     */
    void deleteMyContact(Integer contactId);

    /**
     * 编辑当前登录用户的通讯录联系人（支持修改分组、备注名、备注）——按通讯录记录ID
     * 函数级注释：
     * - 适配外部联系人无 contact_id 的情况，通过 address_book.id 精确定位关系
     * - 若传入 groupId，则校验该分组归属当前用户
     * - alias/remark 传 null 表示不修改，沿用原值
     *
     * @param abId 通讯录记录ID
     * @param groupId 新分组ID（可选）
     * @param alias 新备注名（可选）
     * @param remark 新备注（可选）
     */
    void editMyContactByAbId(Integer abId, Integer groupId, String alias, String remark);

    /**
     * 删除当前登录用户通讯录中的某个联系人——按通讯录记录ID
     * 函数级注释：
     * - 适配外部联系人无 contact_id 的情况，通过 address_book.id 精确删除关系
     *
     * @param abId 通讯录记录ID
     */
    void deleteMyContactByAbId(Integer abId);

    /**
     * 查询当前登录用户的通讯录分组列表。
     * 若用户尚无任何分组，则自动创建一个“默认分组”后再返回。
     * @return 分组列表
     */
    List<AddressGroup> listMyGroups();

    /**
     * 查询当前登录用户的通讯录联系人列表（包含联系人详细信息）。
     * 可根据分组ID进行筛选；若groupId为null，则返回所有分组下的联系人。
     * @param groupId 可选分组ID
     * @return 联系人列表（包含详细信息）
     */
    List<AddressBookContactDTO> listMyContacts(Integer groupId);

    /**
     * 创建当前登录用户的通讯录分组
     * @param groupName 分组名称
     * @return 创建后的分组实体
     */
    AddressGroup createGroup(String groupName);

    /**
     * 删除指定分组并将该分组下的联系人迁移到默认分组
     * @param groupId 待删除的分组ID
     */
    void deleteGroup(Integer groupId);

    /**
     * 重命名当前登录用户的某个分组
     * 业务约束：
     * - 必须登录
     * - 只能重命名属于当前用户的分组
     * - 默认分组不允许重命名
     * - 分组名称去除首尾空格后长度1~20，且在该用户下不允许与其它分组重名
     *
     * @param groupId 分组ID
     * @param newGroupName 新的分组名称
     */
    void renameGroup(Integer groupId, String newGroupName);
}