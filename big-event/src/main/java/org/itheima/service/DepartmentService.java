package org.itheima.service;

import org.itheima.pojo.Department;
import org.itheima.pojo.dto.DepartmentDTO;
import org.itheima.pojo.dto.DepartmentRequest;

import java.util.List;

/**
 * 部门服务接口
 */
public interface DepartmentService {
    
    /**
     * 根据ID查询部门
     * 
     * @param id 部门ID
     * @return 部门信息
     */
    Department findById(Integer id);
    
    /**
     * 查询所有部门
     * 
     * @return 部门列表
     */
    List<Department> findAll();
    
    /**
     * 查询子部门
     * 
     * @param parentId 父部门ID
     * @return 子部门列表
     */
    List<Department> findByParentId(Integer parentId);
    
    /**
     * 添加部门
     * 
     * @param departmentRequest 部门请求参数
     * @return 添加的部门信息
     */
    Department add(DepartmentRequest departmentRequest);
    
    /**
     * 更新部门
     * 
     * @param departmentRequest 部门请求参数
     * @return 更新后的部门信息
     */
    Department update(DepartmentRequest departmentRequest);
    
    /**
     * 删除部门
     * 
     * @param id 部门ID
     * @return 是否删除成功
     */
    boolean deleteById(Integer id);
    
    /**
     * 根据部门名称查询部门
     * 
     * @param departmentName 部门名称
     * @return 部门信息
     */
    Department findByName(String departmentName);
    
    /**
     * 统计部门下的用户数量
     * 
     * @param departmentId 部门ID
     * @return 用户数量
     */
    Integer countUsersByDepartmentId(Integer departmentId);
    
    /**
     * 查询所有部门及其用户数量
     * 
     * @return 部门列表及用户数量
     */
    List<Department> findAllWithUserCount();
    
    /**
     * 获取部门树形结构
     * 
     * @return 部门树形结构
     */
    List<DepartmentDTO> getDepartmentTree();
    
    /**
     * 获取部门及其所有子部门ID列表
     * 
     * @param departmentId 部门ID
     * @return 部门及其所有子部门ID列表
     */
    List<Integer> getDepartmentAndChildrenIds(Integer departmentId);
}