package com.langheng.modules.join.wrapper;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.query.Query;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.langheng.modules.join.conditions.query.JoinQuery;
import com.langheng.modules.join.support.JoinLambdaUtil;
import com.langheng.modules.join.support.JoinPart;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author wuliangyu
 * @date 2021/11/10 4:25 下午
 * @see Query 实现设置查询列的接口(Query)
 */
@Slf4j
public abstract class AbstractJoinWrapper<T, R, Children extends AbstractJoinWrapper<T, R, Children>>
        extends AbstractWrapper<T, R, Children>
        implements JoinQuery<Children, R> {

    /**
     * 查询字段的返回映射类
     */
    @Getter
    @Setter
    private Class<?> selectClass;

    /**
     * 主表名
     */
    @Getter
    @Setter
    protected String tableName;

    /**
     * 主表别名
     */
    @Setter
    protected String tableAlias;

    /**
     * 缓存的查询列
     */
    protected SharedString sqlSelect;

    /**
     * 用户设置的查询字段
     */
    protected List<String> sqlColumn;

    /**
     * 是否设置查询字段,初始值为false
     */
    protected boolean isSetSelect;

    /**
     * 是否设置逻辑删除字段条件,初始值为true
     */
    protected AtomicBoolean isWithLogicDelete;

    /**
     * 是否已添加设置逻辑删除字段条件到条件里（normalExpression）
     */
    protected AtomicBoolean isCacheLogicDelete;

    /**
     * 连表信息缓存map
     */
    protected Map<String, JoinPart> joinPartsMap;

    /**
     * 连表的缓存和别名缓存
     */
    @Getter
    @Setter
    protected Map<Class<?>, String> classAlisMap = new ConcurrentHashMap<>(5);

    /**
     * 添加连表类和别名到缓存
     *
     * @param clazz 类
     * @param alis  别名
     */
    protected void useTableAlis(Class<?> clazz, String alis) {
        classAlisMap.put(clazz, alis);
    }


    /**
     * 构造函数
     *
     * @param entityClass 数据表实体类
     * @param tableAlias  别名
     * @param selectClass DTO类
     */
    protected AbstractJoinWrapper(Class<T> entityClass, String tableAlias, Class<?> selectClass) {
        initNeed();
        //获取主表的表明
        String tableName = this.getClassTableName(entityClass);
        // 防止空表明
        if (StringUtils.isBlank(tableAlias)) {
            tableAlias = generateTableAlias(tableName);
        }

        this.tableName = tableName;
        this.tableAlias = tableAlias;
        this.setSelectClass(selectClass);
        this.setEntityClass(entityClass);
        try {
            this.setEntity(entityClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            log.warn("Class[{}] is failed to init", entityClass.getName());
            e.printStackTrace();
        }
        //是否设置查询
        this.isSetSelect = null == selectClass;
        //使用自定义别名
        useTableAlis(entityClass, tableAlias);
    }

    /**
     * 必要的初始化
     */
    @Override
    protected void initNeed() {
        //连表信息初始化
        this.sqlSelect = SharedString.emptyString();
        this.sqlColumn = new LinkedList<>();
        this.isSetSelect = false;
        this.isWithLogicDelete = new AtomicBoolean(true);
        this.isCacheLogicDelete = new AtomicBoolean(false);
        this.joinPartsMap = new LinkedHashMap<>(5);

        //mybatis-plus 默认初始化方法
        super.initNeed();
    }


    /**
     * 获取预览SQL
     *
     * @return 预览SQL
     */
    public String getFullSql() {
        return String.format("select %s from %s %s %s %s",
                this.getSqlSelect(), this.getTableName(), this.getTableAlias(), this.getJoinPart(), this.getCustomSqlSegment());
    }


    /**
     * 获取设置查询的column
     *
     * @return 返回查询的列
     */
    @Override
    public String getSqlSelect() {
        //获取缓存
        String sqlSelect = this.sqlSelect.getStringValue();
        if (StringUtils.isNotBlank(sqlSelect)) {
            return sqlSelect;
        }

        List<String> sqlSelectList = new ArrayList<>(this.sqlColumn);

        // TODO: 2022/5/23  添加基本查询函数,位置在查询实体类前

        //添加selectClass的字段
        if (isSetSelect) {
            sqlSelectList = this.sqlColumn;
        } else {
            if (null != this.selectClass) {
                List<String> selectClassSql = this.getSelectSql(this.selectClass);
                sqlSelectList.addAll(selectClassSql);
            } else {
                // isSelect 为 false，selectClass 为 null，用主表和连表拼查询字段，默认 main.*,join.*
                // 兜底主表字段
                if (this.getEntity() == null) {
                    throw new MybatisPlusException("ew.entity is null!");
                }

                //主表
                String mainSelect = this.sqlSelectAll(this.tableAlias);
                sqlSelectList.add(mainSelect);
                //连表
                List<String> joinSelect = joinPartsMap.keySet().stream()
                        .map(this::sqlSelectAll)
                        .collect(Collectors.toList());
                sqlSelectList.addAll(joinSelect);
            }
        }
        // 用逗号拼接查询列
        sqlSelect = CollectionUtils.isNotEmpty(sqlSelectList) ?
                stripSqlInjection(String.join(StringPool.COMMA, sqlSelectList)) : null;


        if (StringUtils.isBlank(sqlSelect)) {
            // 兜底查询字段，出现没有，或者出错的情况下
            String columnPrefix = getColumnPrefix();
            //select字段为空时，使用*进行查询
            //返回 alis.*
            sqlSelect = columnPrefix.concat(StringPool.ASTERISK);

        }
        //设置缓存
        this.sqlSelect.setStringValue(sqlSelect);
        return sqlSelect;
    }

    /**
     * 获取类的表字段别名及列，eg：alis.column
     *
     * @param tableInfo 缓存表信息
     * @return 返回类的别名字段
     */
    protected List<String> getTableAlisColumns(TableInfo tableInfo) {
        //获取表名，转换为别名 sys_user -> su
        String alis = JoinLambdaUtil.tableNameToTableAlias(tableInfo.getTableName());
        //获取表的
        List<String> tableColumns = getTableColumns(tableInfo);
        return tableColumns.stream()
                //别名加上列，如 t1.xx
                .map(column -> this.addTableAliasToColumn(alis, column))
                .collect(Collectors.toList());
    }

    /**
     * 将sql中的特殊字符去掉
     *
     * @param sql sql语句
     * @return 返回sql
     */
    private static String stripSqlInjection(String sql) {
        Assert.notNull(sql, "strip sql is null.");
        return sql.replaceAll("('.+--)|(--)|(\\|)|(%7C)", "");
    }

    /**
     * 获取列名需添加的前缀
     *
     * @return 获取别名
     */
    private String getColumnPrefix() {
        if (tableAlias == null) {
            tableAlias = "main";
        }
        if (StringUtils.isEmpty(tableAlias)) {
            return "";
        }
        return tableAlias.concat(StringPool.DOT);
    }


    /**
     * 设置select的column
     *
     * @param selectClass 生成查询字段的类
     * @return 返回别名拼接列
     */
    protected List<String> getSelectSql(Class<?> selectClass) {
        if (selectClass == null) {
            return Collections.emptyList();
        }
        //设置sql字段，（先加个空格防止跟前面的select挨在一起）
        List<String> sqlSelect = new ArrayList<>();

        //连接的表是否使用别名
        Map<String, String> fieldNameAliasNameMap = new ConcurrentHashMap<>(5);
        if (CollectionUtils.isNotEmpty(this.joinPartsMap)) {
            this.joinPartsMap.forEach((alias, joinPart) ->
//                    joinPart.getTableColumnSet().forEach(name-> columnNameAliasNameMap.putIfAbsent(name, s)
            {
                //获取联表的信息
                TableInfo joinTableInfo = TableInfoHelper.getTableInfo(joinPart.getTableName());
                if (joinTableInfo != null) {
                    //以<column,alias>的形式放入map
                    List<TableFieldInfo> joinTableFieldList = joinTableInfo.getFieldList();
                    if (CollectionUtils.isNotEmpty(joinTableFieldList)) {
                        joinTableFieldList.forEach(tableFieldInfo ->
                                fieldNameAliasNameMap.putIfAbsent(tableFieldInfo.getField().getName(), alias));
                    }
                    //判断连表的表里是否有主键
                    if (joinTableInfo.havePK()) {
                        fieldNameAliasNameMap.putIfAbsent(joinTableInfo.getKeyProperty(), alias);
                    }
                }
            });
        }
        //主表信息
        TableInfo tableInfo = TableInfoHelper.getTableInfo(this.tableName);
        List<TableFieldInfo> fieldList = tableInfo.getFieldList();
        if (CollectionUtils.isNotEmpty(fieldList)) {
            fieldList.forEach(tableFieldInfo ->
                    fieldNameAliasNameMap.put(tableFieldInfo.getField().getName(), this.tableAlias));
        }
        //判断连表的表里是否有主键
        if (tableInfo.havePK()) {
            fieldNameAliasNameMap.putIfAbsent(tableInfo.getKeyProperty(), this.tableAlias);
        }
        Field[] fields = ReflectUtil.getFields(selectClass);
        for (Field field : fields) {
            field.setAccessible(true);
            //跳过serialVersionUID
            if ("serialVersionUID".equals(field.getName())) {
                continue;
            }
            //跳过静态类
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            //跳过被标记为不存在的字段
            //用@TableField 注解修饰，并且标记为跳过的，如@TableField(exist = false),或@TableField(select = false)
            TableField tableField = field.getAnnotation(TableField.class);
            if (null != tableField && !tableField.exist()) {
                continue;
            }
            if (null != tableField && !tableField.select()) {
                continue;
            }

            //查询column名
            String columnName = getTableFieldNameByEntityFieldName(field.getName());

            //是否使用别名
            if (StringUtils.isNotBlank(columnName)) {
                //获取列的别名
                String aliasName = fieldNameAliasNameMap.get(field.getName());
                //添加到查询列数组
                sqlSelect.add(StringUtils.isNotBlank(aliasName) ?
                        //列添加别名
                        aliasName.concat(StringPool.DOT).concat(columnName) : columnName);
            }
        }
        return sqlSelect;
    }

    /**
     * 根据数据库实体类字段名称生成数据库表字段名称
     *
     * @param entityFieldName 数据库实体类名称
     * @return 数据库表名称
     */
    public static String getTableFieldNameByEntityFieldName(String entityFieldName) {
        if (StringUtils.isEmpty(entityFieldName)) {
            return "";
        }
        return ReUtil.replaceAll(entityFieldName, "([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * 解析类的属性，转成查询column
     *
     * @param clazz 获取类的表字段
     * @return 返回类的表字段
     */
    public static List<String> getTableColumns(Class<?> clazz) {
        Assert.notNull(clazz, "clazz must be not null");
        TableInfo tableInfo = TableInfoHelper.getTableInfo(clazz);
        Assert.notNull(tableInfo, "Undiscovered table info . " + clazz.getName());

        return getTableColumns(tableInfo);
    }

    /**
     * 解析类的属性，转成查询column
     *
     * @param tableInfo      表缓存
     * @param excludeColumns 不查询字段
     * @return 表字段
     */
    public static List<String> getTableColumns(TableInfo tableInfo, String... excludeColumns) {

        //排除查询字段
        Set<String> excludeColumnsSet = StringUtils.isAnyBlank(excludeColumns) ?
                Collections.emptySet() : Arrays.stream(excludeColumns).collect(Collectors.toSet());

        //获取mybatis类缓存的表字段数组
        List<String> fieldList = tableInfo.getFieldList().stream()
                .map(TableFieldInfo::getColumn)
                .collect(Collectors.toList());
        //缓存表有主键
        if (null != tableInfo.getKeyColumn()) {
            //将主键放到第一的位置
            fieldList.add(0, tableInfo.getKeyColumn());
        }
        //没有主键字段就返回field的数组
        return fieldList.stream()
                //排除
                .filter(column -> !excludeColumnsSet.contains(column))
                .collect(Collectors.toList());
    }

    /**
     * 将数据表实体类转换成数据表表名
     *
     * @param clazz 数据库实体类
     * @return 数据表表明
     */
    protected String getClassTableName(Class<?> clazz) {
        if (clazz == null) {
            return "";
        }
        //尝试从mybatis-plus缓存中获取
        TableInfo tableInfo = TableInfoHelper.getTableInfo(clazz);
        if (null == tableInfo) {
            //没有获取到，直接将该类的名称转换为表明
            log.warn("该连表构造器的实体类没有从Mybatis-Plus缓存中获取");
            return StrUtil.toUnderlineCase(clazz.getSimpleName());
        }
        return tableInfo.getTableName();
    }

    /**
     * 根据数据库表名获取可用的别名（重复时则在后面增加数字后缀）
     *
     * @param tableName 数据库表名
     * @return 别名
     */
    protected String generateTableAlias(String tableName) {
        String alias = JoinLambdaUtil.tableNameToTableAlias(tableName);
        if (!joinPartsMap.containsKey(alias)) {
            //如果别名未存在，则直接返回
            return alias;
        } else {
            //如果别名已存在，则通过增加数字后缀的方式避免冲突
            return findUniqueTableAlias(alias, 1);
        }
    }

    /**
     * 通过修改数字后缀的方式寻找未占用的别名
     *
     * @param alias  别名前缀
     * @param suffix 数字后缀
     * @return 寻找到的可用别名
     */
    protected String findUniqueTableAlias(String alias, int suffix) {
        String finalAlias = alias + suffix;
        //如果该别名已经存在，则将后缀加1，直到没有别名冲突的情况
        if (joinPartsMap.containsKey(finalAlias)) {
            return findUniqueTableAlias(alias, suffix + 1);
        }
        return finalAlias;
    }

    /**
     * 为列名添加别名
     *
     * @param column 列
     * @return 当前主表别名添加的查询列，alis.column
     */
    private String addTableAliasToColumn(String column) {
        if (column == null || column.contains(StringPool.DOT) || column.contains(StringPool.LEFT_BRACKET)) {
            return column;
        }
        return getColumnPrefix().concat(column);
    }

    /**
     * 为列名添加别名
     *
     * @param column 列
     * @param alis   别名
     * @return 返回 alis.column
     */
    private String addTableAliasToColumn(String alis, String column) {
        if (StringUtils.isNotBlank(alis)) {
            if (column == null || column.contains(StringPool.DOT) || column.contains(StringPool.LEFT_BRACKET)) {
                return column;
            }
            return alis.concat(column);
        } else {
            return addTableAliasToColumn(column);
        }
    }

    /**
     * 获取主表名
     *
     * @return 返回当前主表别名alis
     */
    public String getTableAlias() {
        if (StringUtils.isBlank(tableAlias)) {
            tableAlias = generateTableAlias("");
        }
        return tableAlias;
    }

    /**
     * 获取联表部分的SQL
     *
     * @return 联表部分SQL
     */
    public String getJoinPart() {
        if (CollectionUtils.isEmpty(joinPartsMap)) {
            return "";
        }
        //连表的信息
        List<String> joinParts = new ArrayList<>();
        for (JoinPart joinPart : joinPartsMap.values()) {
            // 1.处理连表字段
            //连接数据库字段名称（key为主表字段，value为连接表字段）
            Map<String, String> joinFieldsMap = joinPart.getJoinFieldsMap();
            //拼接连表字段
            String joinFields = StringUtils.joinWith(" AND ", joinFieldsMap.entrySet().stream()
                    .map(joinOn -> {
                        //主表字段
                        String fromField = joinOn.getKey().contains(".") ? joinOn.getKey() :
                                String.format("%s.%s",
                                        //没有设置主表别名，就用当前wrapper设置的主表别名
                                        StringUtils.isEmpty(joinPart.getFromTableAlias()) ? this.tableAlias : joinPart.getFromTableAlias()
                                        , joinOn.getKey());
                        //连表字段
                        String toField = joinOn.getValue().contains(".") ? joinOn.getValue() :
                                joinOn.getValue().startsWith("plain:") ? wrapWithSingleQuote(joinOn.getValue().replace("plain:", "")) :
                                        String.format("%s.%s", joinPart.getTableAlias(), joinOn.getValue());
                        //在连接条件中使用了in
                        boolean useIn = joinOn.getValue().startsWith("in:");
                        if (useIn) {
                            String listStr = joinOn.getValue().replace("in:", "");
                            String[] arr = listStr.split(",");
                            return String.format("%s IN (%s)", fromField,
                                    StringUtils.joinWith(",", Arrays.stream(arr)
                                            .map(this::wrapWithSingleQuote)
                                            .collect(Collectors.toList())));
                        }
                        return String.format("%s = %s", fromField, toField);
                    })
                    .toArray());

            // 2.处理连表 额外条件(apply)
            // apply 是left join t2 on 的额外添加条件
            // 如 select * from table1 t1 LEFT JOIN table2 t2 ON (t1.xx = t2.xx AND apply )
            String apply = StringUtils.isNotBlank(joinPart.getApply()) ?
                    StringUtils.joinWith(StringPool.SPACE, StringPool.AND, joinPart.getApply()) : StringPool.SPACE;
            //返回的条件表达  table1 t1 join table2 t2 on(t1.xx = t2.xx AND apply)
            String joinPartFormat = String.format("%s JOIN %s %s ON( %s %s)",
                    joinPart.getJoinType(),
                    joinPart.getTableName(),
                    joinPart.getTableAlias(),
                    joinFields,
                    apply
            );
            joinParts.add(joinPartFormat);
        }


        //每一个left join 用换行隔开
        return StringUtils.joinWith(StringPool.NEWLINE, joinParts.toArray());
    }

    /**
     * 使用单引号包括指定内容
     *
     * @param value 指定内容
     * @return 处理结果
     */
    private String wrapWithSingleQuote(String value) {
        //返回 'value'
        return StringPool.SINGLE_QUOTE + value + StringPool.SINGLE_QUOTE;
    }


    /**
     * 查询表别名的全部列，如 alis.*
     *
     * @param alis 指定内容
     * @return 别名的全查询列 alis.*
     */
    private String sqlSelectAll(String alis) {
        String concat = StringPool.DOT.concat(StringPool.ASTERISK);
        return StringUtils.isNotBlank(alis) ?
                alis.concat(concat) : StringPool.ASTERISK;
    }


    /**
     * @param columns 字段数组
     * @return children
     */
    @SafeVarargs
    @Override
    public final Children select(R... columns) {
        List<String> sqlSelect = Arrays.stream(columns)
                .map(this::columnToString)
                .collect(Collectors.toList());
        this.sqlColumn.addAll(sqlSelect);
        // sql设置了，isSetSelect 设置为 true
        this.isSetSelect = true;
        return typedThis;
    }

    /**
     * 设置selectClass类查询列
     *
     * @param dtoClass dto
     * @return children
     */
    @Override
    public Children select(Class<?> dtoClass) {
        if (null != dtoClass) {
            this.setSelectClass(dtoClass);
            //手动设置查询字段，
            this.isSetSelect = true;
        }

        return typedThis;
    }

    @Override
    public Children first(boolean condition, String firstSql) {
        throw new MybatisPlusException("JoinWrapper does not support for firstSql");
    }

    /**
     * 忽略逻辑删除查询条件
     *
     * @return children
     */
    public Children ignoreLogic() {
        this.isWithLogicDelete.set(false);
        return this.typedThis;
    }

    @Override
    public String getSqlSegment() {
        //判断是否设置逻辑删除字段和是否已经添加逻辑删除字段条件
        if (isWithLogicDelete.get() && !isCacheLogicDelete.get()) {
            this.classAlisMap.forEach((clazz, alis) -> {
                TableInfo tableInfo = TableInfoHelper.getTableInfo(clazz);
                if (null != tableInfo && tableInfo.isWithLogicDelete()) {
                    String logicDeleteSql = tableInfo.getLogicDeleteSql(false, false);
                    expression.getNormal().add(() ->
                            String.format(" AND %s.%s ", alis, logicDeleteSql));
                }
            });
            isCacheLogicDelete.set(true);
        }
        return super.getSqlSegment();
    }
}
