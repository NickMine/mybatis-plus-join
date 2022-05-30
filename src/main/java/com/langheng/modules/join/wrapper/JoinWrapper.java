package com.langheng.modules.join.wrapper;

import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.langheng.modules.join.support.JoinPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author: wuliangyu
 * @date: 2021/2/22 5:21 下午
 * @description: 连表工具类
 */
public class JoinWrapper<T> extends AbstractJoinWrapper<T, String, JoinWrapper<T>> {

    private static final Logger log = LoggerFactory.getLogger(JoinWrapper.class);

    public final static String SELECT_TEMPLATE = "SELECT ${ew.sqlSelect} FROM ${ew.tableName} ${ew.tableAlias} ${ew.joinPart} ${ew.customSqlSegment}";


    /**
     * Mybatis-plus 新版本已弃用,需要重新构造该枚举类（WrapperKeyword）
     */
    protected enum WrapperKeyword implements ISqlSegment {
        APPLY(null),
        /**
         * 左
         */
        BRACKET_START("["),
        /**
         * 右
         */
        BRACKET_END("]");


        private final String keyword;

        @Override
        public String getSqlSegment() {
            return this.keyword;
        }

        WrapperKeyword(final String keyword) {
            this.keyword = keyword;
        }
    }


    /**
     * 子类返回一个自己的新对象
     */
    @Override
    protected JoinWrapper<T> instance() {
        return new JoinWrapper<>(this.getEntityClass(), tableAlias, this.getSelectClass());
    }

    /**
     * 构造函数
     *
     * @param entityClass 数据表实体类
     * @param selectClass DTO类
     */
    public JoinWrapper(Class<T> entityClass, Class<?> selectClass) {
        super(entityClass, null, selectClass);
    }

    /**
     * 构造函数
     *
     * @param entityClass 数据表实体类
     * @param tableAlias  别名
     * @param selectClass DTO类
     */
    public JoinWrapper(Class<T> entityClass, String tableAlias, Class<?> selectClass) {
        super(entityClass, tableAlias, selectClass);
    }


    /**
     * 联表查询
     *
     * @param joinType  联表类型：LEFT：左连接；RIGHT：右连接
     * @param fromField 联表主表字段
     * @param toField   联表连接表字段
     * @param clazz     连接表实体类
     * @param func      在连接表中的查询条件构造器
     * @param <R>       连接表实体类
     * @return this
     */
    private <R> JoinWrapper<T> join(String joinType, String fromField, String toField, Class<R> clazz, Function<JoinWrapper<R>, JoinWrapper<R>> func) {
        Map<String, String> joinFieldsMap = new HashMap<>(1);
        joinFieldsMap.put(fromField, toField);
        return join(joinType, joinFieldsMap, clazz, func);
    }

    /**
     * 联表查询
     *
     * @param joinType     联表类型：LEFT：左连接；RIGHT：右连接
     * @param joinFieldMap 联表字段，Key为主表字段，Value为连接表字段
     * @param clazz        连接表实体类
     * @param func         在连接表中的查询条件构造器
     * @param <R>          连接表实体类
     * @return this
     */
    private <R> JoinWrapper<T> join(String joinType, Map<String, String> joinFieldMap, Class<R> clazz, Function<JoinWrapper<R>, JoinWrapper<R>> func) {
        String tableName = getClassTableName(clazz);
        JoinWrapper<R> joinWrapper = new JoinWrapper<>(clazz, generateTableAlias(tableName), null);
        joinWrapper.paramNameSeq = this.paramNameSeq;
        joinWrapper.paramNameValuePairs = this.paramNameValuePairs;
        func.apply(joinWrapper);
        //将分组和排序转移到主表条件构造器（否则会混在常规条件中，导致语法报错）
        moveItems(joinWrapper.expression.getGroupBy(), this.expression.getGroupBy());
        moveItems(joinWrapper.expression.getOrderBy(), this.expression.getOrderBy());

        //联表信息
        JoinPart joinPart = new JoinPart()
                .setJoinType(joinType)
                .setJoinClass(clazz)
                .setTableName(joinWrapper.tableName)
                .setTableAlias(joinWrapper.tableAlias)
                .setJoinFieldsMap(joinFieldMap);
        this.joinPartsMap.put(joinWrapper.tableAlias, joinPart);

        //合并联表信息
        joinWrapper.joinPartsMap.forEach((alias, joinPartInfo) -> {
            if (this.joinPartsMap.containsKey(alias)) {
                alias = findUniqueTableAlias(alias, 1);
            }
            //重新设置联表来源表别名和目标表别名
            joinPartInfo.setFromTableAlias(joinWrapper.tableAlias)
                    .setTableAlias(alias);
            this.joinPartsMap.put(alias, joinPartInfo);
        });

        //合并查询字段
        if (CollectionUtils.isNotEmpty(joinWrapper.sqlColumn)) {
            this.sqlColumn.addAll(joinWrapper.sqlColumn);
        }


        //拼接查询条件
        if (joinWrapper.expression.getNormal().isEmpty()) {
            return this;
        } else {
            //执行sql拼接
            this.expression.add(WrapperKeyword.BRACKET_START, joinWrapper, WrapperKeyword.BRACKET_END);
            return this;
        }
    }

    /**
     * 将源列表中的元素移动到目标列表中
     *
     * @param srcList  源列表
     * @param destList 目标列表
     * @param <R>      元素类型
     */
    private static <R> void moveItems(List<R> srcList, List<R> destList) {
        if (srcList == null || destList == null || srcList.isEmpty()) {
            return;
        }
        //由于mybatis-plus自定义了List子类，重新实现了addAll，因此此处只能循环使用add
        srcList.forEach(item -> destList.add(item));
        srcList.clear();
    }


    /**
     * 左连接查询
     *
     * @param joinFieldMap 联表字段，Key为主表字段，Value为连接表字段
     * @param clazz        连接表实体类
     * @param func         在连接表中的查询条件构造器
     * @param <R>          连接表实体类
     * @return this
     */
    public <R> JoinWrapper<T> leftJoin(Map<String, String> joinFieldMap, Class<R> clazz, Function<JoinWrapper<R>, JoinWrapper<R>> func) {
        return join("LEFT", joinFieldMap, clazz, func);
    }

    /**
     * 左连接查询
     *
     * @param fromField 联表主表字段
     * @param toField   联表连接表字段
     * @param clazz     连接表实体类
     * @param func      在连接表中的查询条件构造器
     * @param <R>       连接表实体类
     * @return this
     */
    public <R> JoinWrapper<T> leftJoin(String fromField, String toField, Class<R> clazz, Function<JoinWrapper<R>, JoinWrapper<R>> func) {
        return join("LEFT", fromField, toField, clazz, func);
    }

    /**
     * 右连接查询
     *
     * @param joinFieldMap 联表字段，Key为主表字段，Value为连接表字段
     * @param clazz        连接表实体类
     * @param func         在连接表中的查询条件构造器
     * @param <R>          连接表实体类
     * @return this
     */
    public <R> JoinWrapper<T> rightJoin(Map<String, String> joinFieldMap, Class<R> clazz, Function<JoinWrapper<R>, JoinWrapper<R>> func) {
        return join("RIGHT", joinFieldMap, clazz, func);
    }

    /**
     * 右连接查询
     *
     * @param fromField 联表主表字段
     * @param toField   联表连接表字段
     * @param clazz     连接表实体类
     * @param func      在连接表中的查询条件构造器
     * @param <R>       连接表实体类
     * @return this
     */
    public <R> JoinWrapper<T> rightJoin(String fromField, String toField, Class<R> clazz, Function<JoinWrapper<R>, JoinWrapper<R>> func) {
        return join("RIGHT", fromField, toField, clazz, func);
    }

    /**
     * 内连接查询
     *
     * @param joinFieldMap 联表字段，Key为主表字段，Value为连接表字段
     * @param clazz        连接表实体类
     * @param func         在连接表中的查询条件构造器
     * @param <R>          连接表实体类
     * @return this
     */
    public <R> JoinWrapper<T> innerJoin(Map<String, String> joinFieldMap, Class<R> clazz, Function<JoinWrapper<R>, JoinWrapper<R>> func) {
        return join("INNER", joinFieldMap, clazz, func);
    }

    /**
     * 内连接查询
     *
     * @param fromField 联表主表字段
     * @param toField   联表连接表字段
     * @param clazz     连接表实体类
     * @param func      在连接表中的查询条件构造器
     * @param <R>       连接表实体类
     * @return this
     */
    public <R> JoinWrapper<T> innerJoin(String fromField, String toField, Class<R> clazz, Function<JoinWrapper<R>, JoinWrapper<R>> func) {
        return join("INNER", fromField, toField, clazz, func);
    }
}
