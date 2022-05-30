package com.langheng.modules.join.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author: wuliangyu
 * @date: 2021/2/23 3:19 下午
 * @description: 用于joinWrapper联表缓存联表信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class JoinPart {
    /**
     * 连接类型：LEFT：左连接；RIGHT：右连接
     */
    private String joinType;

    /**
     * 连接表的类型
     */
    private Class<?> joinClass;

    /**
     * 连接表的名称
     */
    private String tableName;

    /**
     * 连接表的别名
     */
    private String tableAlias;

    /**
     * 主表的别名
     */
    private String fromTableAlias;

    /**
     * 连接数据库字段名称（key为主表字段，value为连接表字段）
     */
    private Map<String, String> joinFieldsMap = new LinkedHashMap<>(5);

    /**
     * 连接数据表连表条件
     * <p>
     * 如：select xx from table1 t1 left join table2 t2 on(t1.xx = t2.xx AND <b>apply</b>)
     */
    private String apply;


    /**
     * 连表类型
     */
    public static class JoinType {
        public static final String LEFT_JOIN = "LEFT";
        public static final String RIGHT_JOIN = "RIGHT";
        public static final String INNER_JOIN = "INNER";
    }
}
