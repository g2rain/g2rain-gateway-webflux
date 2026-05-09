package com.g2rain.gateway.model.route;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 网关路由表返回VO
 * 关联表名: route_definition
 * 功能：封装接口返回数据，继承BaseVo复用基础字段逻辑，隔离数据库实体与前端展示层
 *
 * @author G2rain Generator
 */
@Setter
@Getter
@NoArgsConstructor
public class RouteDefinitionVo {
    /**
     * 资源接口标识
     */
    private Long id;

    /**
     * 服务目标地址
     */
    private String endpoint;

    /**
     * 网关路由前缀
     */
    private String routePrefix;

    /**
     * 资源接口名称
     */
    private String name;

    /**
     * 接口请求方法
     */
    private String method;

    /**
     * 接口请求路径
     */
    private String path;
}
