package org.itheima.service.impl;

import jakarta.annotation.Resource;
import org.itheima.mapper.ExternalContactMapper;
import org.itheima.pojo.ExternalContact;
import org.itheima.service.ExternalContactService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 外部联系人业务实现
 */
@Service
public class ExternalContactServiceImpl implements ExternalContactService {

    @Resource
    private ExternalContactMapper externalContactMapper;

    /**
     * 创建外部联系人
     * - 若手机号存在，则可直接返回已存在的记录以避免重复
     * - 在落库前对可选字符串字段做规范化处理：将 null 统一为 ""，并 trim 去除首尾空格
     */
    @Override
    public ExternalContact create(ExternalContact ec) {
        if (ec == null) {
            throw new IllegalArgumentException("外部联系人不能为空");
        }
        if (StringUtils.hasText(ec.getPhone())) {
            ExternalContact existed = externalContactMapper.findByPhone(ec.getPhone());
            if (existed != null) {
                return existed;
            }
        }
        // 规范化可选字段，避免 NOT NULL 列插入 NULL 导致 SQLIntegrityConstraintViolationException
        ec.setName(StringUtils.hasText(ec.getName()) ? ec.getName().trim() : "");
        ec.setPhone(StringUtils.hasText(ec.getPhone()) ? ec.getPhone().trim() : "");
        ec.setEmail(ec.getEmail() == null ? "" : ec.getEmail().trim());
        ec.setCompany(ec.getCompany() == null ? "" : ec.getCompany().trim());
        ec.setPosition(ec.getPosition() == null ? "" : ec.getPosition().trim());

        externalContactMapper.insert(ec);
        return ec;
    }

    /**
     * 根据ID查询
     */
    @Override
    public ExternalContact getById(Integer id) {
        return externalContactMapper.findById(id);
    }

    /**
     * 根据手机号查询
     */
    @Override
    public ExternalContact getByPhone(String phone) {
        return externalContactMapper.findByPhone(phone);
    }

    /**
     * 更新
     * - 在落库前对可选字符串字段做规范化处理：将 null 统一为 ""，并 trim 去除首尾空格
     */
    @Override
    public void update(ExternalContact ec) {
        // 规范化可选字段，避免 NOT NULL 列更新为 NULL 导致 SQLIntegrityConstraintViolationException
        if (ec != null) {
            ec.setName(StringUtils.hasText(ec.getName()) ? ec.getName().trim() : "");
            ec.setPhone(StringUtils.hasText(ec.getPhone()) ? ec.getPhone().trim() : "");
            ec.setEmail(ec.getEmail() == null ? "" : ec.getEmail().trim());
            ec.setCompany(ec.getCompany() == null ? "" : ec.getCompany().trim());
            ec.setPosition(ec.getPosition() == null ? "" : ec.getPosition().trim());
        }
        externalContactMapper.update(ec);
    }

    /**
     * 删除
     */
    @Override
    public void delete(Integer id) {
        externalContactMapper.delete(id);
    }

    /**
     * 搜索
     */
    @Override
    public List<ExternalContact> search(String keyword) {
        return externalContactMapper.search(keyword);
    }
}