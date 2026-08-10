package org.itheima.pojo.dto;

import lombok.Data;

/**
 * 分页查询请求数据传输对象
 */
@Data
public class PageRequest {
    /**
     * 当前页码，从1开始
     */
    private Integer pageNum = 1;
    
    /**
     * 每页记录数
     */
    private Integer pageSize = 10;
    
    /**
     * 排序字段
     */
    private String orderBy;
    
    /**
     * 排序方式：asc-升序，desc-降序
     */
    private String orderDirection = "desc";
    
    /**
     * 获取MySQL分页的起始索引
     */
    public Integer getOffset() {
        return (pageNum - 1) * pageSize;
    }
    
    /**
     * 获取MySQL分页的记录数
     */
    public Integer getLimit() {
        return pageSize;
    }
}