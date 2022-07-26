package com.langheng.modules.join.enums;

/**
 * 常用的sql函数枚举 默认实现
 * 可以自己实现接口 {@link BaseFuncEnum} 自定义函数
 * 目前支持一个占位符,不支持多个%s
 * 本代码移植自MPJ
 * <p>参照mybatis-plus4</p>
 *
 * @author wuliangyu
 */
public enum DefaultFuncEnum implements BaseFuncEnum {

    SUM("SUM(%s)"),
    COUNT("COUNT(%s)"),
    MAX("MAX(%s)"),
    MIN("MIN(%s)"),
    AVG("AVG(%s)"),
    LEN("LEN(%s)");

    private final String sql;

    DefaultFuncEnum(String sql) {
        this.sql = sql;
    }

    @Override
    public String getSql() {
        return this.sql;
    }

}
