package org.itheima.controller;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.itheima.pojo.AddressBook;
import org.itheima.pojo.AddressGroup;
import org.itheima.pojo.Result;
import org.itheima.pojo.dto.AddressBookContactDTO;
import org.itheima.service.AddressBookService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AddressBookController
 * 提供个人通讯录相关接口
 */
@RestController
@RequestMapping("/addressbook")
public class AddressBookController {

    @Resource
    private AddressBookService addressBookService;

    /**
     * 添加联系人到通讯录（可指定分组）
     * POST /addressbook/add
     */
    @PostMapping("/add")
    public Result<AddressBook> add(@RequestBody @Valid AddReq req) {
        AddressBook saved = addressBookService.addToMyAddressBook(req.getContactId(), req.getGroupId(), req.getAlias(), req.getRemark());
        return Result.success(saved);
    }

    /**
     * 添加外部联系人到通讯录（可指定分组）
     * 函数级注释：
     * - 请求体可携带外部联系人的姓名、电话、邮箱、公司、职位信息
     * - 后端会基于手机号进行去重创建或复用外部联系人
     * - 若未指定分组，则自动创建/复用默认分组
     * - 在address_book表中以 contact_type=external + external_id 建立关系
     *
     * POST /addressbook/external/add
     */
    @PostMapping("/external/add")
    public Result<AddressBook> addExternal(@RequestBody @Valid AddExternalReq req) {
        // 组装外部联系人实体并调用服务
        org.itheima.pojo.ExternalContact external = new org.itheima.pojo.ExternalContact();
        external.setName(req.getName());
        external.setPhone(req.getPhone());
        external.setEmail(req.getEmail());
        external.setCompany(req.getCompany());
        external.setPosition(req.getPosition());
        AddressBook saved = addressBookService.addExternalContact(external, req.getGroupId(), req.getAlias(), req.getRemark());
        return Result.success(saved);
    }

    /**
     * 查询当前登录用户的通讯录分组列表
     * GET /addressbook/groups
     */
    @GetMapping("/groups")
    public Result<List<AddressGroup>> listMyGroups() {
        List<AddressGroup> groups = addressBookService.listMyGroups();
        return Result.success(groups);
    }

    /**
     * 查询当前登录用户的通讯录联系人列表（包含联系人详细信息）
     * GET /addressbook/contacts?groupId={可选}
     */
    @GetMapping("/contacts")
    public Result<List<AddressBookContactDTO>> listMyContacts(@RequestParam(value = "groupId", required = false) Integer groupId) {
        List<AddressBookContactDTO> list = addressBookService.listMyContacts(groupId);
        return Result.success(list);
    }

    /**
     * 创建通讯录分组
     * POST /addressbook/groups
     */
    @PostMapping("/groups")
    public Result<AddressGroup> createGroup(@RequestBody @Valid CreateGroupReq req) {
        AddressGroup created = addressBookService.createGroup(req.getGroupName());
        return Result.success(created);
    }

    /**
     * 删除分组（将该组联系人迁移到默认分组后再删除）
     * DELETE /addressbook/groups/{id}
     */
    @DeleteMapping("/groups/{id}")
    public Result<Void> deleteGroup(@PathVariable("id") Integer id) {
        addressBookService.deleteGroup(id);
        return Result.success();
    }

    /**
     * 编辑我的通讯录中的联系人（支持修改分组、备注名、备注）
     * PUT /addressbook/contacts/{contactId}
     */
    @PutMapping("/contacts/{contactId}")
    public Result<Void> editContact(@PathVariable("contactId") Integer contactId,
                                    @RequestBody @Valid EditContactReq req) {
        addressBookService.editMyContact(contactId, req.getGroupId(), req.getAlias(), req.getRemark());
        return Result.success();
    }

    /**
     * 删除我的通讯录中的联系人
     * DELETE /addressbook/contacts/{contactId}
     */
    @DeleteMapping("/contacts/{contactId}")
    public Result<Void> deleteContact(@PathVariable("contactId") Integer contactId) {
        addressBookService.deleteMyContact(contactId);
        return Result.success();
    }

    /**
     * 重命名分组
     * PUT /addressbook/groups/{id}
     */
    @PutMapping("/groups/{id}")
    public Result<Void> renameGroup(@PathVariable("id") Integer id,
                                    @RequestBody @Valid RenameGroupReq req) {
        addressBookService.renameGroup(id, req.getGroupName());
        return Result.success();
    }

    /**
     * 编辑我的通讯录中的联系人（支持修改分组、备注名、备注）——按通讯录记录ID
     * PUT /addressbook/ab/{abId}
     */
    @PutMapping("/ab/{abId}")
    public Result<Void> editContactByAbId(@PathVariable("abId") Integer abId,
                                    @RequestBody @Valid EditContactReq req) {
        addressBookService.editMyContactByAbId(abId, req.getGroupId(), req.getAlias(), req.getRemark());
        return Result.success();
    }

    /**
     * 删除我的通讯录中的联系人——按通讯录记录ID
     * DELETE /addressbook/ab/{abId}
     */
    @DeleteMapping("/ab/{abId}")
    public Result<Void> deleteContactByAbId(@PathVariable("abId") Integer abId) {
        addressBookService.deleteMyContactByAbId(abId);
        return Result.success();
    }

    @Data
    public static class RenameGroupReq {
        @NotNull(message = "分组名称不能为空")
        private String groupName;
    }

    @Data
    public static class AddReq {
        @NotNull(message = "联系人ID不能为空")
        private Integer contactId; // 要添加的联系人ID
        private Integer groupId;   // 可选分组ID
        private String alias;      // 备注名（可选）
        private String remark;     // 备注（可选）
    }

    @Data
    public static class CreateGroupReq {
        @NotNull(message = "分组名称不能为空")
        private String groupName; // 分组名称
    }

    @Data
    public static class EditContactReq {
        /**
         * 可选新分组ID；若不传或为null则保持原分组
         */
        private Integer groupId;
        /**
         * 可选新备注名；若不传或为null则保持原备注名
         */
        private String alias;
        /**
         * 可选新备注；若不传或为null则保持原备注
         */
        private String remark;
    }

    @Data
    public static class AddExternalReq {
        /**
         * 外部联系人姓名（可选：与电话至少填一项）
         */
        private String name;
        /**
         * 外部联系人电话（可选：与姓名至少填一项）
         */
        private String phone;
        /**
         * 外部联系人邮箱（可选）
         */
        private String email;
        /**
         * 外部联系人公司（可选）
         */
        private String company;
        /**
         * 外部联系人职位（可选）
         */
        private String position;
        /**
         * 目标分组ID（可选）
         */
        private Integer groupId;
        /**
         * 备注名（可选）
         */
        private String alias;
        /**
         * 备注（可选）
         */
        private String remark;
    }
}