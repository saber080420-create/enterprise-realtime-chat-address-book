package org.itheima.service.impl;

import lombok.RequiredArgsConstructor;
import org.itheima.mapper.AddressBookMapper;
import org.itheima.mapper.AddressGroupMapper;
import org.itheima.mapper.UserMapper;
import org.itheima.pojo.AddressBook;
import org.itheima.pojo.AddressGroup;
import org.itheima.pojo.User;
import org.itheima.pojo.ExternalContact;
import org.itheima.pojo.dto.AddressBookContactDTO;
import org.itheima.service.AddressBookService;
import org.itheima.service.ExternalContactService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AddressBookServiceImpl
 */
@Service
@RequiredArgsConstructor
public class AddressBookServiceImpl implements AddressBookService {

    private final AddressBookMapper addressBookMapper;
    private final AddressGroupMapper addressGroupMapper;
    private final UserMapper userMapper;
    private final ExternalContactService externalContactService;

    /**
     * 添加联系人到我的通讯录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressBook addToMyAddressBook(Integer contactId, Integer groupId, String alias, String remark) {
        // 1. 获取当前登录用户ID
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");

        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        if (contactId == null) {
            throw new IllegalArgumentException("联系人ID不能为空");
        }
        if (myUserId.equals(contactId)) {
            throw new IllegalArgumentException("不能将自己添加为联系人");
        }

        // 2. 校验联系人是否存在
        User contact = userMapper.findById(contactId);
        if (contact == null) {
            throw new IllegalArgumentException("联系人不存在");
        }

        // 3. 唯一性校验：是否已存在
        AddressBook existed = addressBookMapper.findByUserAndContact(myUserId, contactId);
        if (existed != null) {
            return existed; // 幂等：直接返回已存在记录
        }

        // 4. 处理分组：若未指定groupId，使用或创建默认分组
        Integer finalGroupId = groupId;
        if (finalGroupId == null) {
            AddressGroup defaultGroup = addressGroupMapper.getDefaultGroup(myUserId);
            if (defaultGroup == null) {
                AddressGroup ng = new AddressGroup();
                ng.setUserId(myUserId);
                ng.setGroupName("默认分组");
                ng.setIsDefault(true);
                ng.setSortOrder(0);
                addressGroupMapper.insert(ng);
                finalGroupId = ng.getId();
            } else {
                finalGroupId = defaultGroup.getId();
            }
        } else {
            // 若指定了分组ID，则需校验该分组是否属于当前用户
            AddressGroup group = addressGroupMapper.findById(finalGroupId);
            if (group == null || !myUserId.equals(group.getUserId())) {
                throw new IllegalArgumentException("分组不存在或无权访问");
            }
        }

        // 5. 保存通讯录记录
        AddressBook ab = new AddressBook();
        ab.setUserId(myUserId);
        ab.setContactId(contactId);
        ab.setGroupId(finalGroupId);
        ab.setAlias(alias);
        ab.setRemark(remark);
        addressBookMapper.insert(ab);

        return ab;
    }

    /**
     * 添加外部联系人到当前登录用户的通讯录
     * 函数级注释：
     * - 校验登录状态与入参基本合法性（至少提供姓名或电话）
     * - 通过手机号在外部联系人库中去重，存在则复用，不存在则创建
     * - 若未指定分组ID，自动创建或复用默认分组
     * - 在 address_book 中以 external_id + contact_type='external' 建立关系；若已存在则返回原记录（幂等）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressBook addExternalContact(ExternalContact externalContact, Integer groupId, String alias, String remark) {
        // 1) 登录校验
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        if (externalContact == null) {
            throw new IllegalArgumentException("外部联系人信息不能为空");
        }
        boolean basicFilled = StringUtils.hasText(externalContact.getName()) || StringUtils.hasText(externalContact.getPhone());
        if (!basicFilled) {
            throw new IllegalArgumentException("请至少填写姓名或联系电话");
        }

        // 2) 去重创建或复用外部联系人记录
        ExternalContact savedExternal = externalContactService.create(externalContact);

        // 3) 幂等：检查当前用户是否已经添加过该外部联系人
        AddressBook existed = addressBookMapper.findByUserAndExternal(myUserId, savedExternal.getId());
        if (existed != null) {
            return existed;
        }

        // 4) 确定分组
        Integer finalGroupId = groupId;
        if (finalGroupId == null) {
            AddressGroup defaultGroup = addressGroupMapper.getDefaultGroup(myUserId);
            if (defaultGroup == null) {
                AddressGroup ng = new AddressGroup();
                ng.setUserId(myUserId);
                ng.setGroupName("默认分组");
                ng.setIsDefault(true);
                ng.setSortOrder(0);
                addressGroupMapper.insert(ng);
                finalGroupId = ng.getId();
            } else {
                finalGroupId = defaultGroup.getId();
            }
        } else {
            AddressGroup group = addressGroupMapper.findById(finalGroupId);
            if (group == null || !myUserId.equals(group.getUserId())) {
                throw new IllegalArgumentException("分组不存在或无权访问");
            }
        }

        // 5) 建立通讯录关系（external）
        AddressBook ab = new AddressBook();
        ab.setUserId(myUserId);
        ab.setExternalId(savedExternal.getId());
        ab.setContactType("external");
        ab.setGroupId(finalGroupId);
        ab.setAlias(alias);
        ab.setRemark(remark);
        addressBookMapper.insertExternal(ab);
        return ab;
    }

    /**
     * 查询当前登录用户的通讯录分组列表。
     * 若用户尚无任何分组，则自动创建一个“默认分组”后再返回。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AddressGroup> listMyGroups() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        List<AddressGroup> groups = addressGroupMapper.listByUserId(myUserId);
        if (groups == null || groups.isEmpty()) {
            AddressGroup ng = new AddressGroup();
            ng.setUserId(myUserId);
            ng.setGroupName("默认分组");
            ng.setIsDefault(true);
            ng.setSortOrder(0);
            addressGroupMapper.insert(ng);
            groups = addressGroupMapper.listByUserId(myUserId);
        }
        return groups;
    }

    /**
     * 查询当前登录用户的通讯录联系人列表（包含联系人详细信息）。
     * 可根据分组ID进行筛选；若groupId为null，则返回所有分组下的联系人。
     */
    @Override
    public List<AddressBookContactDTO> listMyContacts(Integer groupId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        // 目前Mapper提供了按用户ID查询的明细方法，如需按分组筛选可扩展SQL。
        List<AddressBookContactDTO> list = addressBookMapper.listContactsWithDetailsByUserId(myUserId);
        if (groupId == null) {
            return list;
        }
        // 内存中过滤一次（数据量通常不大），后续可优化为SQL条件
        return list.stream()
                .filter(item -> groupId.equals(item.getGroupId()))
                .collect(Collectors.toList());
    }

    /**
     * 创建当前登录用户的通讯录分组
     * 规则：
     * - 必须登录
     * - 分组名非空且去除首尾空格后长度在1~20之间
     * - 同一用户下分组名不可重复（与现有分组名完全相同即判定重复）
     * - 默认分组只能系统创建；此处创建的分组 isDefault=false，sortOrder 取当前最大序后+1（若无则为1）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressGroup createGroup(String groupName) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        if (groupName == null) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        String name = groupName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        if (name.length() > 20) {
            throw new IllegalArgumentException("分组名称长度不能超过20个字符");
        }
        // 重名校验
        AddressGroup existed = addressGroupMapper.findByUserIdAndGroupName(myUserId, name);
        if (existed != null) {
            throw new IllegalArgumentException("已存在同名分组");
        }
        // 计算排序：取当前用户分组的最大sort_order + 1，这里简化为列表大小+1
        List<AddressGroup> groups = addressGroupMapper.listByUserId(myUserId);
        int nextSort = (groups == null || groups.isEmpty()) ? 1 : (groups.stream().map(AddressGroup::getSortOrder).filter(o -> o != null).max(Integer::compareTo).orElse(0) + 1);

        AddressGroup ng = new AddressGroup();
        ng.setUserId(myUserId);
        ng.setGroupName(name);
        ng.setIsDefault(false);
        ng.setSortOrder(nextSort);
        addressGroupMapper.insert(ng);
        return ng;
    }

    /**
     * 删除分组：将该分组下联系人迁移到默认分组后，删除分组
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Integer groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("分组ID不能为空");
        }
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        AddressGroup group = addressGroupMapper.findById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("分组不存在");
        }
        if (!myUserId.equals(group.getUserId())) {
            throw new IllegalArgumentException("无权删除该分组");
        }
        if (Boolean.TRUE.equals(group.getIsDefault())) {
            throw new IllegalArgumentException("默认分组不允许删除");
        }
        // 获取默认分组（若没有则创建）
        AddressGroup defaultGroup = addressGroupMapper.getDefaultGroup(myUserId);
        if (defaultGroup == null) {
            AddressGroup ng = new AddressGroup();
            ng.setUserId(myUserId);
            ng.setGroupName("默认分组");
            ng.setIsDefault(true);
            ng.setSortOrder(0);
            addressGroupMapper.insert(ng);
            defaultGroup = ng;
        }
        // 迁移联系人到默认分组
        addressBookMapper.moveContactsToGroup(myUserId, groupId, defaultGroup.getId());
        // 删除分组
        addressGroupMapper.deleteById(groupId);
    }

    /**
     * 编辑当前登录用户的通讯录联系人（支持修改分组、备注名、备注）——按通讯录记录ID
     * 函数级注释：
     * - 通过 address_book.id 与当前用户ID联合校验关系是否存在
     * - 若传入 groupId，则校验该分组是否属于当前用户
     * - alias/remark 传 null 表示不修改，沿用原值
     * - 仅更新 address_book 关系中的 group_id、alias、remark
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editMyContactByAbId(Integer abId, Integer groupId, String alias, String remark) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        if (abId == null) {
            throw new IllegalArgumentException("通讯录记录ID不能为空");
        }
        AddressBook existed = addressBookMapper.findByIdAndUser(myUserId, abId);
        if (existed == null) {
            throw new IllegalArgumentException("该联系人不在您的通讯录中");
        }
        Integer finalGroupId = existed.getGroupId();
        if (groupId != null) {
            AddressGroup group = addressGroupMapper.findById(groupId);
            if (group == null || !myUserId.equals(group.getUserId())) {
                throw new IllegalArgumentException("分组不存在或无权访问");
            }
            finalGroupId = groupId;
        }
        String finalAlias = alias != null ? alias : existed.getAlias();
        String finalRemark = remark != null ? remark : existed.getRemark();
        addressBookMapper.updateByUserAndAb(myUserId, abId, finalGroupId, finalAlias, finalRemark);
    }

    /**
     * 删除当前登录用户通讯录中的某个联系人——按通讯录记录ID
     * 函数级注释：
     * - 通过 address_book.id 与当前用户ID联合校验关系是否存在
     * - 校验通过后删除 address_book 关系记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyContactByAbId(Integer abId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        if (abId == null) {
            throw new IllegalArgumentException("通讯录记录ID不能为空");
        }
        AddressBook existed = addressBookMapper.findByIdAndUser(myUserId, abId);
        if (existed == null) {
            throw new IllegalArgumentException("该联系人不在您的通讯录中");
        }
        addressBookMapper.deleteByUserAndAb(myUserId, abId);
    }

    /**
     * 重命名分组
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameGroup(Integer groupId, String newGroupName) {
        if (groupId == null) {
            throw new IllegalArgumentException("分组ID不能为空");
        }
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        AddressGroup group = addressGroupMapper.findById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("分组不存在");
        }
        if (!myUserId.equals(group.getUserId())) {
            throw new IllegalArgumentException("无权重命名该分组");
        }
        if (Boolean.TRUE.equals(group.getIsDefault())) {
            throw new IllegalArgumentException("默认分组不允许重命名");
        }
        if (newGroupName == null) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        String name = newGroupName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        if (name.length() > 20) {
            throw new IllegalArgumentException("分组名称长度不能超过20个字符");
        }
        // 重名校验（允许与自己同名不报错）
        AddressGroup existed = addressGroupMapper.findByUserIdAndGroupName(myUserId, name);
        if (existed != null && !existed.getId().equals(groupId)) {
            throw new IllegalArgumentException("已存在同名分组");
        }
        int rows = addressGroupMapper.updateGroupName(groupId, name);
        if (rows <= 0) {
            throw new IllegalStateException("更新分组名称失败");
        }
    }

    /**
     * 编辑当前登录用户的通讯录联系人（支持修改分组、备注名、备注）——按联系人用户ID
     * 函数级注释：
     * - 仅适用于内部联系人（存在 contact_id），外部联系人请使用 abId 版本的接口
     * - 若传入 groupId，则校验该分组是否属于当前登录用户
     * - alias/remark 传 null 表示不修改，沿用原值
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editMyContact(Integer contactId, Integer groupId, String alias, String remark) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        if (contactId == null) {
            throw new IllegalArgumentException("联系人ID不能为空");
        }
        AddressBook existed = addressBookMapper.findByUserAndContact(myUserId, contactId);
        if (existed == null) {
            throw new IllegalArgumentException("该联系人不在您的通讯录中");
        }
        Integer finalGroupId = existed.getGroupId();
        if (groupId != null) {
            AddressGroup group = addressGroupMapper.findById(groupId);
            if (group == null || !myUserId.equals(group.getUserId())) {
                throw new IllegalArgumentException("分组不存在或无权访问");
            }
            finalGroupId = groupId;
        }
        String finalAlias = alias != null ? alias : existed.getAlias();
        String finalRemark = remark != null ? remark : existed.getRemark();
        addressBookMapper.updateByUserAndContact(myUserId, contactId, finalGroupId, finalAlias, finalRemark);
    }

    /**
     * 删除当前登录用户通讯录中的某个联系人——按联系人用户ID
     * 函数级注释：
     * - 仅适用于内部联系人（存在 contact_id），外部联系人请使用 abId 版本的接口
     * - 先校验该联系人是否在我的通讯录中
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyContact(Integer contactId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer myUserId = (Integer) claims.get("id");
        if (myUserId == null) {
            throw new IllegalStateException("未登录或会话失效");
        }
        if (contactId == null) {
            throw new IllegalArgumentException("联系人ID不能为空");
        }
        AddressBook existed = addressBookMapper.findByUserAndContact(myUserId, contactId);
        if (existed == null) {
            throw new IllegalArgumentException("该联系人不在您的通讯录中");
        }
        addressBookMapper.deleteByUserAndContact(myUserId, contactId);
    }
}