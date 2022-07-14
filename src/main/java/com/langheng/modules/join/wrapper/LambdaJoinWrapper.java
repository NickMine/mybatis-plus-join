package com.langheng.modules.join.wrapper;

import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.core.toolkit.support.SerializedLambda;
import com.langheng.modules.join.support.AlisColumnCache;
import com.langheng.modules.join.support.JoinLambdaUtil;
import com.langheng.modules.join.support.JoinPart;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * date: 2021/10/27 11:51 上午
 * description:
 *
 * @author wuliangyu
 */
@Slf4j
public class LambdaJoinWrapper<Main>
        extends AbstractJoinWrapper<Main, SFunction<Main, ?>, LambdaJoinWrapper<Main>> {

    public LambdaJoinWrapper(Class<Main> entityClass, String tableAlias, Class<?> selectClass) {
        super(entityClass, tableAlias, selectClass);
    }

    public LambdaJoinWrapper(Class<Main> entityClass, Class<?> selectClass) {
        super(entityClass, null, selectClass);
    }

    /**
     * 获取当前wrapper的表的别名
     *
     * @param tableAlias 表的别名
     */
    @Override
    public void setTableAlias(String tableAlias) {
        if (!StringUtils.isNotBlank(tableAlias)) {
            tableAlias = JoinLambdaUtil.tableNameToTableAlias(getClassTableName(getEntityClass()));
        }
        this.tableAlias = tableAlias;
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
     * 用于生成嵌套 sql
     * <p>故 sqlSelect 不向下传递</p>
     */
    @Override
    protected LambdaJoinWrapper<Main> instance() {
        LambdaJoinWrapper<Main> lambdaJoinWrapper = new LambdaJoinWrapper<>(getEntityClass(), getTableAlias(), getSelectClass());
        lambdaJoinWrapper.classAlisMap = this.classAlisMap;
        lambdaJoinWrapper.paramNameSeq = this.paramNameSeq;
        lambdaJoinWrapper.paramNameValuePairs = this.paramNameValuePairs;
        return lambdaJoinWrapper;
    }


    /**
     * 连表方法，不传别名，则是使用默认表明
     *
     * @param joinClass  连表类
     * @param mainColumn 主表字段,如：User::getUserId
     * @param joinColumn 连表字段,如：Class::getClassId
     * @param <Join>     连表类型
     * @return this
     */
    public <Join> LambdaJoinWrapper<Main> leftJoin(
            Class<Join> joinClass,
            SFunction<Main, ?> mainColumn,
            SFunction<Join, ?> joinColumn,
            Function<LambdaJoinWrapper<Join>, LambdaJoinWrapper<Join>> function) {
        return this.join(JoinPart.JoinType.LEFT_JOIN, null, joinClass, mainColumn, joinColumn, function);
    }

    /**
     * 连表方法，不传别名，则是使用默认表明
     *
     * @param joinClass  连表类
     * @param mainColumn 主表字段,如：User::getUserId
     * @param joinColumn 连表字段,如：Class::getClassId
     * @param <Join>     连表类型
     * @return this
     */
    public <Join> LambdaJoinWrapper<Main> leftJoin(
            Class<Join> joinClass,
            SFunction<Main, ?> mainColumn,
            SFunction<Join, ?> joinColumn) {
        return this.join(JoinPart.JoinType.LEFT_JOIN, null, joinClass, mainColumn, joinColumn, Function.identity());
    }

    /**
     * 连表方法，不传别名，则是使用默认表明
     *
     * @param joinClass  连表类
     * @param mainColumn 主表字段,如：User::getUserId
     * @param joinColumn 连表字段,如：Class::getClassId
     * @param <Join>     连表类型
     * @return this
     */
    public <Join> LambdaJoinWrapper<Main> rightJoin(
            Class<Join> joinClass,
            SFunction<Main, ?> mainColumn,
            SFunction<Join, ?> joinColumn,
            Function<LambdaJoinWrapper<Join>, LambdaJoinWrapper<Join>> function) {
        return this.join(JoinPart.JoinType.RIGHT_JOIN, null, joinClass, mainColumn, joinColumn, function);
    }

    /**
     * 连表方法，不传别名，则是使用默认表明
     *
     * @param joinClass  连表类
     * @param mainColumn 主表字段,如：User::getUserId
     * @param joinColumn 连表字段,如：Class::getClassId
     * @param <Join>     连表类型
     * @return this
     */
    public <Join> LambdaJoinWrapper<Main> innerJoin(
            Class<Join> joinClass,
            SFunction<Main, ?> mainColumn,
            SFunction<Join, ?> joinColumn,
            Function<LambdaJoinWrapper<Join>, LambdaJoinWrapper<Join>> function) {
        return this.join(JoinPart.JoinType.INNER_JOIN, null, joinClass, mainColumn, joinColumn, function);
    }

    /**
     * 连表方法，不传别名，则是使用默认表明
     *
     * @param joinType   联表类型
     * @param joinClass  连表类
     * @param mainColumn 主表字段,如：User::getUserId
     * @param joinColumn 连表字段,如：Class::getClassId
     * @param <Join>     连表类型
     * @return this
     */
    public <Join> LambdaJoinWrapper<Main> join(
            String joinType,
            String joinTableAlias,
            Class<Join> joinClass,
            SFunction<Main, ?> mainColumn,
            SFunction<Join, ?> joinColumn,
            Function<LambdaJoinWrapper<Join>, LambdaJoinWrapper<Join>> function) {
        //获取表信息
        String joinTableName = TableInfoHelper.getTableInfo(joinClass).getTableName();
        //生成别名
        if (StringUtils.isBlank(joinTableAlias)) {
            joinTableAlias = generateTableAlias(joinTableName);
        } else {
            //缓存自定义别名
            super.useTableAlis(joinClass, joinTableAlias);
        }
        //主表字段
        String mainField = alisColumnToString(mainColumn);
        //联表字段
        String joinField = alisColumnToString(joinColumn);
        //生成连表字段Map
        Map<String, String> joinFieldsMap = new HashMap<>(1);
        joinFieldsMap.put(mainField, joinField);
        //生成联表缓存联表信息
        JoinPart joinPart = new JoinPart();
        joinPart.setTableAlias(joinTableAlias)
                .setJoinClass(joinClass)
                .setJoinType(joinType)
                .setTableName(joinTableName)
                .setFromTableAlias(getTableAlias())
                .setJoinFieldsMap(joinFieldsMap);
        //添加到连表信息Map中
        this.joinPartsMap.put(joinTableAlias, joinPart);
        //将连表添加到缓存map中
        classAlisMap.put(joinClass, joinTableAlias);

        //子实例
        LambdaJoinWrapper<Join> child =
                new LambdaJoinWrapper<>(joinClass, this.getSelectClass());
        child.paramNameSeq = this.paramNameSeq;
        function.apply(child);
        String apply = child.getExpression().getNormal().stream()
                .map(ISqlSegment::getSqlSegment)
                .collect(Collectors.joining(StringPool.SPACE));
        joinPart.setApply(apply);
        this.paramNameValuePairs.putAll(child.paramNameValuePairs);
        return this;
    }


    /**
     * 解析lambda表达式
     *
     * @param column 实体类的lambda表达式
     * @return 返回表字段，如 t1.user_id
     */
    @Override
    protected String columnToString(SFunction<Main, ?> column) {
        return alisColumnToString(column);
    }

    /**
     * 解析lambda表达式
     *
     * @param column 实体类的lambda表达式
     * @return 返回表字段，如 t1.user_id
     */
    private String alisColumnToString(SFunction<?, ?> column) {
        //解析表达式
        SerializedLambda serializedLambda = LambdaUtils.resolve(column);
        //获取列明
        String fieldName = PropertyNamer.methodToProperty(serializedLambda.getImplMethodName());
        //获取实体类类型
        Class<?> instantiatedClass = serializedLambda.getInstantiatedType();
        //尝试缓存
        JoinLambdaUtil.tryInitCache(instantiatedClass);
        //获取缓存
        AlisColumnCache alisColumnCache = JoinLambdaUtil.getAlisColumnCache(fieldName, instantiatedClass);
        //lambda表达式的类是否有自定义别名
        if (classAlisMap.containsKey(instantiatedClass)) {
            //有自定义别名，直接拼接
            String alis = classAlisMap.get(instantiatedClass);
            return alis
                    .concat(StringPool.DOT)
                    .concat(alisColumnCache.getColumn());
        }
        //lambda表达式的类没有自定义，使用缓存
        return alisColumnCache.getAlisColumn();
    }


    /**
     * lambda表达式转实体对应字段
     *
     * @param column     实体类的lambda表达式
     * @param onlyColumn 获取列,true:没有别名(user_id);false:有别名(t1.user_id)
     * @return 列
     */
    protected <T> String columnToString(SFunction<T, ?> column, boolean onlyColumn) {
        AlisColumnCache alisColumnCache = getAlisColumnCache(column);
        return onlyColumn ? alisColumnCache.getColumn() : alisColumnCache.getAlisColumn();
    }

    /**
     * 获取lambda表达式缓存
     * 获取 SerializedLambda 对应的列信息，从 lambda 表达式中推测实体类
     * 如果获取不到列信息，那么本次条件组装将会失败
     *
     * @param column 实体类的lambda表达式
     * @return 实体类对应列缓存
     */
    private <T> AlisColumnCache getAlisColumnCache(SFunction<T, ?> column) {
        //解析表达式
        SerializedLambda serializedLambda = LambdaUtils.resolve(column);
        //获取列明
        String fieldName = PropertyNamer.methodToProperty(serializedLambda.getImplMethodName());
        //获取实体类类型
        Class<?> instantiatedClass = serializedLambda.getInstantiatedType();
        //尝试缓存
        JoinLambdaUtil.tryInitCache(instantiatedClass);
        //获取缓存
        return JoinLambdaUtil.getAlisColumnCache(fieldName, instantiatedClass);
    }

    /**
     * 调用连表条件前强转
     *
     * @param <Join> 连表类
     * @return this
     */
    public <Join> LambdaJoinWrapper<Join> joinTo(Class<Join> joinClass) {
        //检验是否在连表缓存中
        if (null == joinClass || !classAlisMap.containsKey(joinClass)) {
            throw new MybatisPlusException(
                    //该表不在连表缓存中
                    MessageFormat.format("this class[{0}] not in joined table map！", joinClass, getEntityClass()));
        }
        return (LambdaJoinWrapper<Join>) this;
    }


    public <M> LambdaJoinWrapper<M> main(Class<M> mainClass) {
        //检查是否为主表
        if (!getEntityClass().equals(mainClass)) {
            throw new MybatisPlusException("该表不是主表");
        }
        return (LambdaJoinWrapper<M>) typedThis;
    }
}
