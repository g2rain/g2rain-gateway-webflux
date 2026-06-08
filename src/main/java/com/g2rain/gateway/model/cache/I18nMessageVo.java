package com.g2rain.gateway.model.cache;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * 国际化信息表返回VO
 * 关联表名: i18n_message
 * 功能：封装接口返回数据，继承BaseVo复用基础字段逻辑，隔离数据库实体与前端展示层
 *
 * @author Aplha
 */
@Setter
@Getter
@NoArgsConstructor
public class I18nMessageVo {

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
     * 国际化用途编码
     */
    private String messageUsageCode;

    /**
     * 业务标签
     */
    private String tag;

    /**
     * 语言编码,如 zh
     */
    private String languageCode;

    /**
     * 国家/地区编码,如 CN
     */
    private String regionCode;

    /**
     * 国际化消息编码(唯一)
     */
    private String messageCode;

    /**
     * 国际化内容文本
     */
    private String messageText;

    /**
     * 扩展字段,存储额外格式化内容
     */
    private String extendField;
}
