package com.langheng.modules.join.support;

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wuliangyu
 * @date 2021/12/24 1:02 上午
 * @description 连表lambda表达式工具
 */

@Slf4j
public class JoinLambdaUtil {

    public static final String SELECT_TEMPLATE = "<script> SELECT ${ew.sqlSelect} FROM ${ew.tableName} ${ew.tableAlias} ${ew.joinPart} ${ew.customSqlSegment} \n</script>";


    /**
     * 表字段（column）和 实体字段（entity属性）缓存map
     * <p>
     * Map<tableName,Map<实体字段，表字段>>
     */
    private static final ConcurrentHashMap<Class<?>, Map<String, AlisColumnCache>> COLUMN_CACHE_MAP
            = new ConcurrentHashMap<>(5);

    /**
     * 表的默认别名，都是以表的映射类生成，可能会出现重名，请注意
     * eg：ass_nominate 和 ass_note,两个表都是 "an"
     * 做好区分
     */
    private static final ConcurrentHashMap<Class<?>, String> DEFAULT_TABLE_ALIS_CACHE_MAP
            = new ConcurrentHashMap<>(5);

    /**
     * 别名缓存类
     */
    private static final Set<String> ALIS_SET
            = new ConcurrentHashSet<>();

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
            //放入缓存
            DEFAULT_TABLE_ALIS_CACHE_MAP.put(aClass, tableAlis);
            //将字段和属性遍历放入缓存
            columnMap.forEach((property, columnCache) -> {
                String column = columnCache.getColumn();
                alisColumnMap.put(property,
                        new AlisColumnCache(
                                StringUtils.joinWith(StringPool.DOT, tableAlis, column),
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
        }
        return alis;
    }


    public static ColumnCache getColumnCache(String fieldName, Class<?> aClass) {
        return LambdaUtils.getColumnMap(aClass).get(LambdaUtils.formatKey(fieldName));
    }

    public static AlisColumnCache getAlisColumnCache(String fieldName, Class<?> aClass) {
        return COLUMN_CACHE_MAP.get(aClass).get(LambdaUtils.formatKey(fieldName));
    }

    /**
     * 根据数据库表名生成别名（可能重复）（通常取首字母，比如sys_user的别名为su，）
     *
     * @param tableName 数据库
     * @return 别名
     */
    public static String tableNameToTableAlias(String tableName) {
        List<String> resultList = ReUtil.findAll("_(\\w)", tableName, 1);
        //如果未找到匹配项，则直接使用表名的前3个字符（不含下划线）作为别名
        if (resultList == null || resultList.isEmpty()) {
            tableName = tableName.replace("_", "");
            return tableName.length() >= 3 ? tableName.substring(0, 3) : tableName;
        }
        return tableName.substring(0, 1).concat(StrUtil.join("", resultList.toArray()));
    }
}
