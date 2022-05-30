package com.langheng.modules.join.support;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * @author wuliangyu
 * @date 2021/12/23 7:24 下午
 * @description
 */
@Data
@AllArgsConstructor
public class AlisColumnCache implements Serializable {

    private static final long serialVersionUID = -4586291538088403456L;

    /**
     * 别名和字段连起来的查询字段
     * 如：su.login_code
     */
    private String alisColumn;

    /**
     * 表的别名 alis
     */
    private String alis;

    /**
     * 使用 column
     */
    private String column;
    /**
     * 查询 column
     */
    private String columnSelect;
}
