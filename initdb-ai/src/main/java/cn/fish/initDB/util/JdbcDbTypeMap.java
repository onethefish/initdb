package cn.fish.initDB.util;


import cn.hutool.core.map.MapUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;


/**
 * @author DongJu Chen
 */
@Slf4j
public class JdbcDbTypeMap {
    /**
     * 类型转换映射Mapping
     */
    private static final Map<String, String[]> JavaTypeMapping = new HashMap<>();
    private static final Map<String, String> JavaTypeColumnSize = new HashMap<>();
    private static final Map<String, String> columnSize = new HashMap<>();

    static {
        JavaTypeMapping.put("DECIMAL", new String[]{"DECIMAL", "DOUBLE", "NUMERIC", "NUMBER"});
        JavaTypeMapping.put("STRING", new String[]{"VARCHAR", "VARCHAR2", "NVARCHAR2", "CHARACTER", "VARYING", "CHARACTER VARYING"});
        JavaTypeMapping.put("CHAR", new String[]{"CHAR"});
        JavaTypeMapping.put("INTEGER", new String[]{"INTEGER", "TINYINT", "INT","INT4","INT2","SERIAL"});
        JavaTypeMapping.put("BIGINT", new String[]{"BIGINT"});
        JavaTypeMapping.put("DATE", new String[]{"DATE", "YEAR"});
        JavaTypeMapping.put("BLOB", new String[]{"BLOB", "LONGBLOB", "MEDIUMBLOB"});
        JavaTypeMapping.put("CLOB", new String[]{"CLOB", "LONGTEXT", "TEXT", "MEDIUMTEXT"});
        JavaTypeMapping.put("TIMESTAMP", new String[]{"TIME", "TIMESTAMP", "DATETIME"});
    }

    static {
        JavaTypeColumnSize.put("INTEGER", "");
        JavaTypeColumnSize.put("BIGINT", "");
        JavaTypeColumnSize.put("TIMESTAMP", "");
        JavaTypeColumnSize.put("BLOB", "");
        JavaTypeColumnSize.put("CLOB", "");
        JavaTypeColumnSize.put("DATE", "");
    }

    public static String getJavaType(String dbTypeCurrent) throws Exception {
        for (String key : JavaTypeMapping.keySet()) {
            String[] dbTypeUnion = JavaTypeMapping.get(key);
            for (String dbType : dbTypeUnion) {
                if (dbType.equalsIgnoreCase(dbTypeCurrent)) {
                    return key;
                }
            }
        }
        log.warn(dbTypeCurrent + "找不到正确的类型，请修改，JdbcDbTypeMap的JavaTypeMapping初始化");
        return "UNKNOWN";
    }

    public static String getColumnSize(String javaType, String columnSizeCurrent) {
        return MapUtil.getStr(JavaTypeColumnSize, javaType, columnSizeCurrent);
    }

    public static String getColumnSize(String columnName, String columnSizeCurrent, String decimalDigits) {
        return MapUtil.getStr(columnSize, columnName, columnSizeCurrent);
    }
}
