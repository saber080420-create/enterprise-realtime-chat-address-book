package org.itheima.service.impl;

import org.itheima.mapper.DepartmentMapper;
import org.itheima.pojo.Department;
import org.itheima.pojo.dto.DepartmentDTO;
import org.itheima.pojo.dto.DepartmentRequest;
import org.itheima.service.DepartmentService;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet; // 新增：循环检测使用
import java.util.Set;     // 新增：循环检测使用

/**
 * 部门服务实现类
 */
@Service
public class DepartmentServiceImpl implements DepartmentService {

    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    public Department findById(Integer id) {
        return departmentMapper.findById(id);
    }

    @Override
    public List<Department> findAll() {
        return departmentMapper.findAll();
    }

    @Override
    public List<Department> findByParentId(Integer parentId) {
        return departmentMapper.findByParentId(parentId);
    }

    @Override
    @Transactional
    public Department add(DepartmentRequest departmentRequest) {
        // 检查部门名称是否已存在
        Department existingDepartment = departmentMapper.findByName(departmentRequest.getDepartmentName());
        if (existingDepartment != null) {
            throw new RuntimeException("部门名称已存在");
        }
        
        // 获取当前登录用户ID
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        // 创建新部门对象
        Department department = new Department();
        department.setDepartmentName(departmentRequest.getDepartmentName());
        department.setParentId(departmentRequest.getParentId());
        department.setDescription(departmentRequest.getDescription());
        department.setCreateUserId(userId);
        department.setCreateTime(LocalDateTime.now());
        department.setUpdateTime(LocalDateTime.now());
        
        // 保存部门
        departmentMapper.add(department);
        
        return department;
    }

    @Override
    @Transactional
    public Department update(DepartmentRequest departmentRequest) {
        // 检查部门是否存在
        Department existingDepartment = departmentMapper.findById(departmentRequest.getId());
        if (existingDepartment == null) {
            throw new RuntimeException("部门不存在");
        }
        
        // 检查部门名称是否已被其他部门使用
        Department departmentWithSameName = departmentMapper.findByName(departmentRequest.getDepartmentName());
        if (departmentWithSameName != null && !departmentWithSameName.getId().equals(departmentRequest.getId())) {
            throw new RuntimeException("部门名称已存在");
        }
        
        // 更新部门信息
        existingDepartment.setDepartmentName(departmentRequest.getDepartmentName());
        existingDepartment.setParentId(departmentRequest.getParentId());
        existingDepartment.setDescription(departmentRequest.getDescription());
        existingDepartment.setUpdateTime(LocalDateTime.now());
        
        // 保存更新
        departmentMapper.update(existingDepartment);
        
        return existingDepartment;
    }

    @Override
    @Transactional
    public boolean deleteById(Integer id) {
        // 检查部门是否存在
        Department department = departmentMapper.findById(id);
        if (department == null) {
            throw new RuntimeException("部门不存在");
        }
        
        // 检查是否有子部门
        List<Department> children = departmentMapper.findByParentId(id);
        if (children != null && !children.isEmpty()) {
            throw new RuntimeException("该部门下存在子部门，无法删除");
        }
        
        // 检查部门下是否有用户
        Integer userCount = departmentMapper.countUsersByDepartmentId(id);
        if (userCount > 0) {
            throw new RuntimeException("该部门下存在用户，无法删除");
        }
        
        // 删除部门
        departmentMapper.deleteById(id);
        
        return true;
    }

    @Override
    public Department findByName(String departmentName) {
        return departmentMapper.findByName(departmentName);
    }

    @Override
    public Integer countUsersByDepartmentId(Integer departmentId) {
        return departmentMapper.countUsersByDepartmentId(departmentId);
    }

    @Override
    public List<Department> findAllWithUserCount() {
        return departmentMapper.findAllWithUserCount();
    }

    @Override
    public List<DepartmentDTO> getDepartmentTree() {
        try {
            // 获取所有部门
            List<Department> allDepartments = departmentMapper.findAll();
            
            // 检查空数据情况
            if (allDepartments == null || allDepartments.isEmpty()) {
                System.out.println("警告：未找到任何部门数据");
                return new ArrayList<>();
            }
            
            // 构建部门树
            List<DepartmentDTO> rootDepartments = new ArrayList<>();
            
            // 先找出所有顶级部门
            for (Department department : allDepartments) {
                if (department.getParentId() == null || department.getParentId() == 0) {
                    DepartmentDTO dto = convertToDepartmentDTO(department);
                    rootDepartments.add(dto);
                }
            }
            
            // 为每个顶级部门构建子部门树（新增：循环检测与深度限制）
            for (DepartmentDTO rootDepartment : rootDepartments) {
                // 每个根节点使用独立的访问集，避免跨根污染
                Set<Integer> path = new HashSet<>();
                buildDepartmentTree(rootDepartment, allDepartments, path, 0);
            }
            
            System.out.println("成功构建部门树，根部门数量：" + rootDepartments.size());
            return rootDepartments;
            
        } catch (Exception e) {
            System.err.println("构建部门树发生异常：" + e.getMessage());
            e.printStackTrace();
            // 返回空列表而不是抛出异常，避免500错误
            return new ArrayList<>();
        }
    }

    @Override
    public List<Integer> getDepartmentAndChildrenIds(Integer departmentId) {
        List<Integer> departmentIds = new ArrayList<>();
        departmentIds.add(departmentId);
        
        // 递归获取所有子部门ID
        addChildDepartmentIds(departmentId, departmentIds);
        
        return departmentIds;
    }
    
    /**
     * 递归构建部门树（新增安全防护）
     * 函数级注释：
     * - 通过 path 集合记录当前递归路径上的部门ID，若再次遇到已存在的ID，判定为“环形引用/自循环”，直接跳过，避免栈溢出
     * - 通过 depth 限制递归最大深度，默认上限 100，防止异常数据导致过深的树形结构
     * - 所有异常均使用 try-catch 包裹，出现个别节点异常时不影响整棵树构建
     *
     * @param parent           父部门DTO
     * @param allDepartments   所有部门列表
     * @param path             当前递归路径访问集（用于检测环）
     * @param depth            当前深度
     */
    private void buildDepartmentTree(DepartmentDTO parent, List<Department> allDepartments, Set<Integer> path, int depth) {
        if (parent == null || allDepartments == null) {
            return;
        }

        // 最大深度保护（可根据业务调整）
        final int MAX_DEPTH = 100;
        if (depth > MAX_DEPTH) {
            System.err.println("警告：部门树深度超过上限，已中断构建。parentId=" + parent.getId());
            return;
        }

        // 环形依赖检测
        Integer parentId = parent.getId();
        if (parentId != null) {
            if (path.contains(parentId)) {
                System.err.println("检测到部门环形引用，已跳过。parentId=" + parentId);
                return;
            }
            path.add(parentId);
        }
        
        List<DepartmentDTO> children = new ArrayList<>();
        
        try {
            for (Department department : allDepartments) {
                if (department != null && department.getParentId() != null && 
                    parent.getId() != null && department.getParentId().equals(parent.getId())) {
                    
                    DepartmentDTO child = convertToDepartmentDTO(department);
                    if (child != null) {
                        children.add(child);
                        // 递归构建子部门（对子调用传递 path 副本，确保每条路径独立）
                        buildDepartmentTree(child, allDepartments, new HashSet<>(path), depth + 1);
                    }
                }
            }
        } catch (Throwable t) {
            // 使用 Throwable 捕获所有可能错误（包含Error），保证不会因个别节点导致整体失败
            System.err.println("构建子部门时发生严重错误：" + t.getMessage());
        }
        
        parent.setChildren(children);
        
        // 设置部门员工数量，增加空值保护
        try {
            Integer employeeCount = departmentMapper.countUsersByDepartmentId(parent.getId());
            parent.setEmployeeCount(employeeCount != null ? employeeCount : 0);
        } catch (Exception e) {
            System.err.println("获取部门" + parent.getId() + "员工数量失败：" + e.getMessage());
            parent.setEmployeeCount(0);
        }
    }
    
    /**
     * 将Department实体转换为DepartmentDTO
     *
     * @param department 部门实体
     * @return 部门DTO
     */
    private DepartmentDTO convertToDepartmentDTO(Department department) {
        if (department == null) {
            return null;
        }
        
        try {
            DepartmentDTO dto = new DepartmentDTO();
            BeanUtils.copyProperties(department, dto);
            
            // 确保子部门列表和员工数量不为null
            dto.setChildren(new ArrayList<>());
            dto.setEmployeeCount(0);
            
            return dto;
        } catch (Exception e) {
            System.err.println("转换Department到DTO失败：" + e.getMessage());
            return null;
        }
    }
    
    /**
     * 递归获取所有子部门ID
     * 
     * @param departmentId 部门ID
     * @param departmentIds 部门ID列表
     */
    private void addChildDepartmentIds(Integer departmentId, List<Integer> departmentIds) {
        List<Department> children = departmentMapper.findByParentId(departmentId);
        
        if (children != null && !children.isEmpty()) {
            for (Department child : children) {
                departmentIds.add(child.getId());
                // 递归获取子部门的子部门ID
                addChildDepartmentIds(child.getId(), departmentIds);
            }
        }
    }
}