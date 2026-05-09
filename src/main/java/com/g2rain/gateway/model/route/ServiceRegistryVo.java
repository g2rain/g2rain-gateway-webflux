package com.g2rain.gateway.model.route;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.g2rain.common.json.AdminCompanyCondition;
import com.g2rain.common.json.ConditionalJsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 服务注册表返回VO
 * 关联表名: service_registry
 * 功能：封装接口返回数据，继承BaseVo复用基础字段逻辑，隔离数据库实体与前端展示层
 *
 * @author G2rain Generator
 */
@Setter
@Getter
@NoArgsConstructor
public class ServiceRegistryVo {
    /**
     * 主键
     */
    private Long id;

    /**
     * 更新时间（出参格式：yyyy-MM-dd HH:mm:ss）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private String updateTime;

    /**
     * 创建时间（出参格式：yyyy-MM-dd HH:mm:ss）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private String createTime;

    /**
     * 服务逻辑编码
     */
    private String serviceCode;

    /**
     * 服务显示名称
     */
    private String name;

    /**
     * 服务目标地址
     */
    private String endpoint;

    /**
     * 网关路由前缀
     */
    private String routePrefix;

    /**
     * 后端服务说明
     */
    private String description;

    /**
     * 删除标识[0:未删除, 1:已删除]
     */
    @ConditionalJsonIgnore(adminCompany = AdminCompanyCondition.TRUE)
    private Boolean deleteFlag;
}
