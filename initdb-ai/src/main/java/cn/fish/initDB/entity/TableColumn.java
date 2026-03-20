package cn.fish.initDB.entity;

import cn.fish.initDB.util.JdbcDbTypeMap;
import cn.hutool.core.collection.CollUtil;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class TableColumn {
    private String columnName;
    private String remarks;
    private String columnType;
    private String javaType;
    private String columnSize;
    private String columnDef;

    private String decimalDigits;

    private boolean isPk;
    private boolean isNullable;
    private List<String> indexNames = new ArrayList<>();

    public void setColumnInfo(String columnName, String columnType, String columnSize, String decimalDigits, String columnDef,
                              boolean isNullable) throws Exception {
        this.columnName = columnName;
        this.columnType = columnType;
        this.javaType = JdbcDbTypeMap.getJavaType(this.columnType);
        this.columnSize = JdbcDbTypeMap.getColumnSize(this.javaType, columnSize);
        this.decimalDigits = decimalDigits;
        this.columnDef = columnDef;
        this.isNullable = isNullable;
    }

    public String getNullableStr() {
        if (isNullable) {
            return "";
        }
        return "N";
    }

    public String getPkStr() {
        if (isPk) {
            return "Y";
        }
        return "";
    }

    public String getIndexNamesStr() {
        // todo
        if (CollUtil.isNotEmpty(indexNames)) {
            return String.join(",", indexNames);
        }
        return "";
    }
}
