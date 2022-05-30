package com.langheng.modules.join.conditions.query;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;

import java.io.Serializable;
import java.util.function.Predicate;

/**
 * @author wuliangyu
 * @date 2022/5/25 9:50 上午
 * @description
 */
public interface JoinQuery<Children,R> extends Serializable {

    /**
     * 设置查询字段
     *
     * @param columns 字段数组
     * @return children
     */
    @SuppressWarnings("unchecked")
    Children select(R... columns);

    Children select(Class<?> dtoClass);
}
