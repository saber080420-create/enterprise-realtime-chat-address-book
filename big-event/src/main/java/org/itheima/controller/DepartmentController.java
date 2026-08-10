package org.itheima.controller;

import jakarta.validation.Valid;
import org.itheima.pojo.Department;
import org.itheima.pojo.Result;
import org.itheima.pojo.dto.DepartmentDTO;
import org.itheima.pojo.dto.DepartmentRequest;
import org.itheima.service.DepartmentService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;



import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 部门管理控制器
 */
@RestController
@RequestMapping("/department")
@Validated
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    /**
     * 获取所有部门
     * 
     * @return 部门列表
     */
    @GetMapping
    public Result<List<Department>> getAllDepartments() {
        List<Department> departments = departmentService.findAll();
        return Result.success(departments);
    }

    /**
     * 根据ID获取部门
     * 
     * @param id 部门ID
     * @return 部门信息
     */
    @GetMapping("/{id}")
    public Result<Department> getDepartmentById(@PathVariable Integer id) {
        Department department = departmentService.findById(id);
        if (department == null) {
            return Result.error("部门不存在");
        }
        return Result.success(department);
    }

    /**
     * 添加部门
     * 
     * @param departmentRequest 部门请求信息
     * @return 添加结果
     */
    @PostMapping
    public Result<Department> addDepartment(@RequestBody @Valid DepartmentRequest departmentRequest) {
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            String currentUserRole = (String) claims.get("role");
            
            // 检查权限：只有系统管理员和部门管理员可以添加部门
            if (!"system_admin".equals(currentUserRole) && !"department_admin".equals(currentUserRole)) {
                return Result.error("权限不足，只有系统管理员和部门管理员可以添加部门");
            }
            
            Department addedDepartment = departmentService.add(departmentRequest);
            return Result.success(addedDepartment);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新部门
     * 
     * @param id 部门ID
     * @param departmentRequest 部门请求信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result<Department> updateDepartment(@PathVariable Integer id, @RequestBody @Valid DepartmentRequest departmentRequest) {
        departmentRequest.setId(id);
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            String currentUserRole = (String) claims.get("role");
            Integer currentUserDepartmentId = (Integer) claims.get("departmentId");
            
            // 检查权限：系统管理员可以更新任何部门，部门管理员只能更新自己所在的部门
            if (!"system_admin".equals(currentUserRole)) {
                if (!"department_admin".equals(currentUserRole)) {
                    return Result.error("权限不足，只有系统管理员和部门管理员可以更新部门");
                }
                
                // 部门管理员只能更新自己所在的部门
                Department department = departmentService.findById(id);
                if (department == null || !currentUserDepartmentId.equals(id)) {
                    return Result.error("权限不足，部门管理员只能更新自己所在的部门");
                }
            }
            
            Department updatedDepartment = departmentService.update(departmentRequest);
            return Result.success(updatedDepartment);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除部门
     * 
     * @param id 部门ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteDepartment(@PathVariable Integer id) {
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            String currentUserRole = (String) claims.get("role");
            
            // 检查权限：只有系统管理员可以删除部门
            if (!"system_admin".equals(currentUserRole)) {
                return Result.error("权限不足，只有系统管理员可以删除部门");
            }
            
            boolean result = departmentService.deleteById(id);
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据名称查询部门
     * 
     * @param name 部门名称
     * @return 部门信息
     */
    @GetMapping("/search")
    public Result<Department> searchDepartmentByName(@RequestParam String name) {
        Department department = departmentService.findByName(name);
        if (department == null) {
            return Result.error("部门不存在");
        }
        return Result.success(department);
    }

    /**
     * 获取部门及其用户数量
     * 
     * @return 部门及用户数量列表
     */
    @GetMapping("/withUserCount")
    public Result<List<Department>> getDepartmentsWithUserCount() {
        List<Department> result = departmentService.findAllWithUserCount();
        return Result.success(result);
    }

    /**
     * 获取部门树形结构
     *
     * 注意：此接口在 WebConfig 中已加入白名单，未登录也可访问用于登录/注册页选择部门
     * 加强健壮性：避免内部异常导致 500，统一返回友好的业务错误
     * 
     * @return 部门树形结构
     */
    @GetMapping("/tree")
    public Result<List<DepartmentDTO>> getDepartmentTree() {
        try {
            System.out.println("开始获取部门树形结构");
            List<DepartmentDTO> tree = departmentService.getDepartmentTree();
            System.out.println("成功获取部门树数据，数量：" + (tree == null ? 0 : tree.size()));
            
            // 验证树形结构数据的完整性
            if (tree != null) {
                for (DepartmentDTO dept : tree) {
                    // 确保每个部门的子部门列表都不为null
                    if (dept.getChildren() == null) {
                        dept.setChildren(new ArrayList<>());
                    }
                    // 确保employeeCount不为null
                    if (dept.getEmployeeCount() == null) {
                        dept.setEmployeeCount(0);
                    }
                }
            } else {
                tree = new ArrayList<>();
            }
            
            // 避免将完整结果拼接到日志中，防止深树或异常结构导致toString递归问题
            System.out.println("返回部门树数据，根节点数量：" + (tree == null ? 0 : tree.size()));
            return Result.success(tree);
        } catch (Throwable e) { // 扩大兜底范围，避免Error导致500
            // 记录详细错误，前端展示业务错误，避免 500 影响登录页
            System.err.println("获取部门树失败，异常详情：" + e.getMessage());
            e.printStackTrace();
            return Result.error("获取部门树失败：" + (e.getMessage() == null ? "服务内部错误" : e.getMessage()));
        }
    }
}