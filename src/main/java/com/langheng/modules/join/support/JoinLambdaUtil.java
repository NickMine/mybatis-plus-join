package com.langheng.modules.join.support;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.ibatis.reflection.property.PropertyNamer;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.core.toolkit.support.SerializedLambda;

import lombok.extern.slf4j.Slf4j;

/**
 * @author wuliangyu
 * @date 2021/12/24 1:02 上午
 * @description 连表lambda表达式工具
 */

@Slf4j
public class JoinLambdaUtil {

    /**
     * 连表模版sql
     */
    public static final String SELECT_TEMPLATE = "<script> SELECT ${ew.sqlSelect} FROM ${ew.tableName} ${ew.tableAlias} ${ew.joinPart} ${ew.customSqlSegment} \n</script>";


    /**
     * 表字段（column）和 实体字段（entity属性）缓存map <br>
     * 表的默认别名，都是以表的映射类生成，可能会出现重名，请注意 <br>
     * eg：<b>ass_nominate</b> 和 <b>ass_note</b>,两个表都是 "an" <br>
     * 做好区分 <br>
     * Map<'tableName',Map<'实体字段'，表字段>> <br>
     */
    private static final ConcurrentHashMap<Class<?>, Map<String, AlisColumnCache>> COLUMN_CACHE_MAP
            = new ConcurrentHashMap<>(5);

    /**
     * 别名缓存类
     */
    private static final Set<String> ALIS_SET
            = new ConcurrentSkipListSet<>();

    /**
     * 初始化类缓存方法
     *
     * @param aClass 缓存的类
     */
    public static void tryInitCache(Class<?> aClass) {
        //查看缓存中是否存在
        if (!COLUMN_CACHE_MAP.containsKey(aClass)) {
            initCache(aClass);
        }
    }

    /**
     * 加锁保证初始化缓存唯一性
     *
     * @param aClass 初始化缓存类
     */
    private synchronized static void initCache(Class<?> aClass) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(aClass);
        //为空，则是不是在TableInfo缓存中，就不是数据库表
        if (null == tableInfo) {
            log.error("该类不是数据库表的映射类，请确认该类：{}", aClass.getName());
            throw new IllegalStateException(
                    MessageFormat.format("this class is not mapped dataBase table list,please check this class:{}",
                            aClass.getName()));
        } else {
            //获取字段数据，放入缓存
            Map<String, ColumnCache> columnMap = LambdaUtils.getColumnMap(aClass);
            Map<String, AlisColumnCache> alisColumnMap = new ConcurrentHashMap<>(columnMap.size());
            //获取表别名
            String tableAlis = tableNameToUniqueTableAlias(tableInfo.getTableName());
            //将字段和属性遍历放入缓存
            columnMap.forEach((property, columnCache) -> {
                String column = columnCache.getColumn();
                alisColumnMap.put(property,
                        new AlisColumnCache(
                                String.join(StringPool.DOT, tableAlis, column),
                                tableAlis,
                                column,
                                columnCache.getColumnSelect()
                        )
                );
            });
            COLUMN_CACHE_MAP.put(aClass, alisColumnMap);
        }
    }

    /**
     * 通过表名获取别名，唯一别名<br>
     * 用后缀序号区分，可能表的加载会改变别名<br>
     * 暂无策略固定表别名重复时，所生成的序号每次启动都相同，即每次启动，表别名可能不一样<br>
     *
     * @param tableName 表名
     * @return 返回唯一别名
     */
    private static String tableNameToUniqueTableAlias(String tableName) {
        //通过表名获取别名
        String alis = tableNameToTableAlias(tableName);
        if (ALIS_SET.contains(alis)) {
            int i = 1;
            do {
                //重复别名后缀加1
                alis += alis + i;
                i++;
            } while (ALIS_SET.contains(alis));
            ALIS_SET.add(alis);
        }
        return alis;
    }

    public static AlisColumnCache getAlisColumnCache(String fieldName, Class<?> aClass) {
        return COLUMN_CACHE_MAP.get(aClass).get(LambdaUtils.formatKey(fieldName));
    }

    /**
     * 根据数据库表名生成别名（可能重复）（通常取首字母，比如<b>sys_user</b>的别名为<b>su</b>，）
     *
     * @param tableName 数据库
     * @return 别名
     */
    public static String tableNameToTableAlias(String tableName) {
        List<String> resultList = JoinLambdaUtil.findAll("_(\\w)", tableName, 1);
        //如果未找到匹配项，则直接使用表名的前3个字符（不含下划线）作为别名
        if (CollectionUtils.isEmpty(resultList)) {
            tableName = tableName.replace("_", "");
            return tableName.length() >= 3 ? tableName.substring(0, 3) : tableName;
        }
        final StringBuilder stringBuilder = new StringBuilder(tableName.substring(0, 1));
        resultList.forEach(stringBuilder::append);
        return stringBuilder.toString();
    }


    /**
     * 取得内容中匹配的所有结果
     *
     * @param regex   正则
     * @param content 被查找的内容
     * @param group   正则的分组
     * @return 结果集
     */
    public static ArrayList<String> findAll(String regex, CharSequence content, int group) {
        //编译后的正则模式
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);

        //返回的集合类型
        ArrayList<String> collection = new ArrayList<>();

        final Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            collection.add(matcher.group(group));
        }
        return collection;
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
        Set<String> excludeColumnsSet = new HashSet<>();
        if (null != excludeColumns && 0 < excludeColumns.length) {
            for (String excludeColumn : excludeColumns) {
                if (StringUtils.isNotBlank(excludeColumn)) {
                    excludeColumnsSet.add(excludeColumn);
                }
            }
        }

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
     * 获取类的表字段别名及列，eg：<b>alis.column</b>
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
     * 获取lambda表达式的属性名
     *
     * @param column 类属性的lambda表达式
     * @return 返回属性名
     */
    public String getColumnToString(SFunction<?, ?> column) {
        //解析表达式
        SerializedLambda serializedLambda = LambdaUtils.resolve(column);
        //获取列名
        return PropertyNamer.methodToProperty(serializedLambda.getImplMethodName());
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
            throw new MybatisPlusException("table alis is null or blank");
        }
    }


    /**
     * 获取查询表（tableInfo）逻辑删除正常字段<br>
     * 如:<br>
     * 表:student;<br>
     * 别名:s;<br>
     * 逻辑删除字段(@TableLogic修饰): status;<br>
     * 结果 ==>  AND s.status = '0'<br>
     *
     * @param alis      别名
     * @param tableInfo 表缓存信息
     * @return 返回结果
     */
    public static String andNormalSql(String alis, TableInfo tableInfo) {
        //获取缓存表的逻辑删除字段
        TableFieldInfo logicDeleteFieldInfo = tableInfo
                .getLogicDeleteFieldInfo();
        //逻辑删除条件sql
        String logicDeleteSql;
        //获取逻辑未删除值
        String value = logicDeleteFieldInfo
                .getLogicNotDeleteValue();
        if (StringPool.NULL.equalsIgnoreCase(value)) {
            logicDeleteSql = logicDeleteFieldInfo.getColumn() + " IS NULL";
        } else {
            logicDeleteSql = logicDeleteFieldInfo.getColumn() + StringPool.EQUALS + String.format(logicDeleteFieldInfo.isCharSequence() ? "'%s'" : "%s", value);
        }
        return String.format(" AND %s.%s ", alis, logicDeleteSql);
    }
}
