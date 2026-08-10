package org.itheima.service;

import org.itheima.pojo.ExternalContact;

import java.util.List;

/**
 * 外部联系人业务接口
 */
public interface ExternalContactService {

    /**
     * 新增外部联系人
     * @param ec 外部联系人实体
     * @return 保存后的实体（包含ID）
     */
    ExternalContact create(ExternalContact ec);

    /**
     * 根据ID查询
     */
    ExternalContact getById(Integer id);

    /**
     * 根据手机号查询（去重）
     */
    ExternalContact getByPhone(String phone);

    /**
     * 更新
     */
    void update(ExternalContact ec);

    /**
     * 删除
     */
    void delete(Integer id);

    /**
     * 关键词搜索
     */
    List<ExternalContact> search(String keyword);
}