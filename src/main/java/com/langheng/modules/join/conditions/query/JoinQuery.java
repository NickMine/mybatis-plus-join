package com.langheng.modules.join.conditions.query;

import java.io.Serializable;

import com.langheng.modules.join.enums.BaseFuncEnum;

/**
 * @author wuliangyu
 * @date 2022/5/25 9:50 上午
 * @description
 */
public interface JoinQuery<Children, R> extends Serializable {

    /**
     * 设置查询字段
     *
     * @param columns 字段数组
     * @return children
     */
    @SuppressWarnings("unchecked")
    Children select(R... columns);

    /**
     * 设置查询字段从dto类中获取<br>
     * 如果表中没有对应字段将跳过<br>
     *
     * @param dtoClass 查询类
     * @return children
     */
    Children select(Class<?> dtoClass);

    /**
     * 查询函数列
     * <p>select xx(*) as alias</p>
     */
    Children selectFun(BaseFuncEnum fun, R column);
}
