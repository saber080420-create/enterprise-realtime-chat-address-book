package org.itheima.controller;

import jakarta.validation.Valid;
import org.itheima.pojo.*;
import org.itheima.pojo.dto.AddressBookContactDTO;
import org.itheima.pojo.dto.DepartmentDTO;
import org.itheima.pojo.dto.DepartmentRequest;
import org.itheima.service.AddressBookService;
import org.itheima.service.DepartmentService;
import org.itheima.service.FrequentContactService;
import org.itheima.service.UserService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OrgController：组织与通讯录门面
 * 注意：仅新增对外命名空间，不移除原有 /department、/addressbook、/contact/frequent 路径。
 * 所有实现直接复用现有 Service，保持参数与返回一致。
 */
@RestController
@RequestMapping("/org")
@Validated
public class OrgController {

    @Autowired
    private DepartmentService departmentService;
    @Autowired
    private AddressBookService addressBookService;
    @Autowired
    private FrequentContactService frequentContactService;
    @Autowired
    private UserService userService;

    // ===== 部门相关（映射 /org/department/**） =====

    @GetMapping("/department")
    public Result<List<Department>> getAllDepartments() {
        List<Department> departments = departmentService.findAll();
        return Result.success(departments);
    }

    @GetMapping("/department/{id}")
    public Result<Department> getDepartmentById(@PathVariable Integer id) {
        Department department = departmentService.findById(id);
        if (department == null) {
            return Result.error("部门不存在");
        }
        return Result.success(department);
    }

    /**
     * 获取部门成员列表（等价于 /user/byDepartment/{id} 的权限与行为）
     */
    @GetMapping("/department/{id}/users")
    public Result<List<User>> getDepartmentUsers(@PathVariable Integer id) {
        try {
            Map<String, Object> claims = ThreadLocalUtil.get();
            String role = (String) claims.get("role");
            Integer currentUserId = (Integer) claims.get("id");
            // 权限：系统管理员任意查看；部门管理员与普通员工可查看“自己所在部门”
            boolean isSystemAdmin = "system_admin".equals(role);
            if (!isSystemAdmin) {
                Integer myDeptId = null;
                try {
                    org.itheima.pojo.User me = userService.findById(currentUserId);
                    if (me != null) myDeptId = me.getDepartmentId();
                } catch (Exception ignore) {}
                if (myDeptId == null) {
                    return Result.error("未分配部门，无法查看成员");
                }
                // 非系统管理员仅允许查看“自己所在的部门”
                id = myDeptId;
            }
            List<User> users = userService.findByDepartmentId(id);
            return Result.success(users);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/department")
    public Result<Department> addDepartment(@RequestBody @Valid DepartmentRequest departmentRequest) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        String currentUserRole = (String) claims.get("role");
        if (!"system_admin".equals(currentUserRole) && !"department_admin".equals(currentUserRole)) {
            return Result.error("权限不足，只有系统管理员和部门管理员可以添加部门");
        }
        Department addedDepartment = departmentService.add(departmentRequest);
        return Result.success(addedDepartment);
    }

    @PutMapping("/department/{id}")
    public Result<Department> updateDepartment(@PathVariable Integer id, @RequestBody @Valid DepartmentRequest departmentRequest) {
        departmentRequest.setId(id);
        Map<String, Object> claims = ThreadLocalUtil.get();
        String currentUserRole = (String) claims.get("role");
        Integer currentUserDepartmentId = (Integer) claims.get("departmentId");
        if (!"system_admin".equals(currentUserRole)) {
            if (!"department_admin".equals(currentUserRole)) {
                return Result.error("权限不足，只有系统管理员和部门管理员可以更新部门");
            }
            Department department = departmentService.findById(id);
            if (department == null || !currentUserDepartmentId.equals(id)) {
                return Result.error("权限不足，部门管理员只能更新自己所在的部门");
            }
        }
        Department updatedDepartment = departmentService.update(departmentRequest);
        return Result.success(updatedDepartment);
    }

    @DeleteMapping("/department/{id}")
    public Result<Boolean> deleteDepartment(@PathVariable Integer id) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        String currentUserRole = (String) claims.get("role");
        if (!"system_admin".equals(currentUserRole)) {
            return Result.error("权限不足，只有系统管理员可以删除部门");
        }
        boolean result = departmentService.deleteById(id);
        return Result.success(result);
    }

    @GetMapping("/department/search")
    public Result<Department> searchDepartmentByName(@RequestParam String name) {
        Department department = departmentService.findByName(name);
        if (department == null) {
            return Result.error("部门不存在");
        }
        return Result.success(department);
    }

    @GetMapping("/department/withUserCount")
    public Result<List<Department>> getDepartmentsWithUserCount() {
        List<Department> result = departmentService.findAllWithUserCount();
        return Result.success(result);
    }

    @GetMapping("/department/tree")
    public Result<List<DepartmentDTO>> getDepartmentTree() {
        try {
            List<DepartmentDTO> tree = departmentService.getDepartmentTree();
            if (tree != null) {
                for (DepartmentDTO dept : tree) {
                    if (dept.getChildren() == null) dept.setChildren(new ArrayList<>());
                    if (dept.getEmployeeCount() == null) dept.setEmployeeCount(0);
                }
            } else {
                tree = new ArrayList<>();
            }
            return Result.success(tree);
        } catch (Throwable e) {
            return Result.error("获取部门树失败：" + (e.getMessage() == null ? "服务内部错误" : e.getMessage()));
        }
    }

    // ===== 通讯录相关（映射 /org/addressbook/**） =====

    @PostMapping("/addressbook/add")
    public Result<AddressBook> add(@RequestBody @Valid AddressBookController.AddReq req) {
        AddressBook saved = addressBookService.addToMyAddressBook(req.getContactId(), req.getGroupId(), req.getAlias(), req.getRemark());
        return Result.success(saved);
    }

    @PostMapping("/addressbook/external/add")
    public Result<AddressBook> addExternal(@RequestBody @Valid AddressBookController.AddExternalReq req) {
        ExternalContact external = new ExternalContact();
        external.setName(req.getName());
        external.setPhone(req.getPhone());
        external.setEmail(req.getEmail());
        external.setCompany(req.getCompany());
        external.setPosition(req.getPosition());
        AddressBook saved = addressBookService.addExternalContact(external, req.getGroupId(), req.getAlias(), req.getRemark());
        return Result.success(saved);
    }

    @GetMapping("/addressbook/groups")
    public Result<List<AddressGroup>> listMyGroups() {
        List<AddressGroup> groups = addressBookService.listMyGroups();
        return Result.success(groups);
    }

    @GetMapping("/addressbook/contacts")
    public Result<List<AddressBookContactDTO>> listMyContacts(@RequestParam(value = "groupId", required = false) Integer groupId) {
        List<AddressBookContactDTO> list = addressBookService.listMyContacts(groupId);
        return Result.success(list);
    }

    @PostMapping("/addressbook/groups")
    public Result<AddressGroup> createGroup(@RequestBody @Valid AddressBookController.CreateGroupReq req) {
        AddressGroup created = addressBookService.createGroup(req.getGroupName());
        return Result.success(created);
    }

    @DeleteMapping("/addressbook/groups/{id}")
    public Result<Void> deleteGroup(@PathVariable("id") Integer id) {
        addressBookService.deleteGroup(id);
        return Result.success();
    }

    @PutMapping("/addressbook/contacts/{contactId}")
    public Result<Void> editContact(@PathVariable("contactId") Integer contactId,
                                    @RequestBody @Valid AddressBookController.EditContactReq req) {
        addressBookService.editMyContact(contactId, req.getGroupId(), req.getAlias(), req.getRemark());
        return Result.success();
    }

    @DeleteMapping("/addressbook/contacts/{contactId}")
    public Result<Void> deleteContact(@PathVariable("contactId") Integer contactId) {
        addressBookService.deleteMyContact(contactId);
        return Result.success();
    }

    @PutMapping("/addressbook/groups/{id}")
    public Result<Void> renameGroup(@PathVariable("id") Integer id,
                                    @RequestBody @Valid AddressBookController.RenameGroupReq req) {
        addressBookService.renameGroup(id, req.getGroupName());
        return Result.success();
    }

    @PutMapping("/addressbook/ab/{abId}")
    public Result<Void> editContactByAbId(@PathVariable("abId") Integer abId,
                                    @RequestBody @Valid AddressBookController.EditContactReq req) {
        addressBookService.editMyContactByAbId(abId, req.getGroupId(), req.getAlias(), req.getRemark());
        return Result.success();
    }

    @DeleteMapping("/addressbook/ab/{abId}")
    public Result<Void> deleteContactByAbId(@PathVariable("abId") Integer abId) {
        addressBookService.deleteMyContactByAbId(abId);
        return Result.success();
    }

    // ===== 常用联系人（映射 /org/contact/frequent/**） =====

    @GetMapping("/contact/frequent")
    public Result<List<User>> getFrequentContacts(@RequestParam(defaultValue = "10") Integer limit) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        List<User> contacts = frequentContactService.findMostFrequentWithUserInfoByUserId(userId, limit);
        return Result.success(contacts);
    }

    @GetMapping("/contact/frequent/favorites")
    public Result<List<User>> getFavoriteContacts() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        List<User> contacts = frequentContactService.findFavoritesWithUserInfoByUserId(userId);
        return Result.success(contacts);
    }

    @GetMapping("/contact/frequent/most-frequent")
    public Result<List<User>> getMostFrequentContacts(@RequestParam(defaultValue = "5") Integer limit) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        List<User> contacts = frequentContactService.findMostFrequentWithUserInfoByUserId(userId, limit);
        return Result.success(contacts);
    }

    @PutMapping("/contact/frequent/favorite/{contactId}")
    public Result<FrequentContact> setFavoriteStatus(@PathVariable Integer contactId,
                                                     @RequestParam Boolean isFavorite) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        FrequentContact contact = frequentContactService.setFavoriteStatus(userId, contactId, isFavorite);
        return Result.success(contact);
    }

    @DeleteMapping("/contact/frequent/{contactId}")
    public Result<Boolean> deleteFrequentContact(@PathVariable Integer contactId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        boolean result = frequentContactService.deleteByUserIdAndContactId(userId, contactId);
        return Result.success(result);
    }

    @GetMapping("/contact/frequent/{contactId}")
    public Result<Map<String, Object>> getContactDetail(@PathVariable Integer contactId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        Map<String, Object> detail = frequentContactService.getContactDetail(userId, contactId);
        return Result.success(detail);
    }
}


