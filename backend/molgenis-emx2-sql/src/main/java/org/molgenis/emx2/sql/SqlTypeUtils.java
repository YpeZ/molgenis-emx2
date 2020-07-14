package org.molgenis.emx2.sql;

import org.jooq.DataType;
import org.jooq.impl.SQLDataType;
import org.molgenis.emx2.*;
import org.molgenis.emx2.utils.TypeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jooq.impl.DSL.cast;
import static org.molgenis.emx2.sql.SqlJavascriptValidator.validateValue;

public class SqlTypeUtils extends TypeUtils {

  private SqlTypeUtils() {
    // to hide the public constructor
  }

  //  static DataType jooqTypeOf(Column column) {
  //    ColumnType sqlColumnType = column.getColumnType();
  //    switch (sqlColumnType) {
  //      case REF:
  //        return jooqTypeOf(column.getRefColumn());
  //      case REFBACK:
  //      case REF_ARRAY:
  //      case MREF:
  //        return jooqTypeOf(column.getRefColumn()).getArrayDataType();
  //      default:
  //        return jooqTypeOf(sqlColumnType);
  //    }
  //  }

  static DataType jooqTypeOf(ColumnType columnType) {
    switch (columnType) {
      case UUID:
        return SQLDataType.UUID;
      case UUID_ARRAY:
        return SQLDataType.UUID.getArrayDataType();
      case STRING:
        return SQLDataType.VARCHAR(255);
      case STRING_ARRAY:
        return SQLDataType.VARCHAR(255).getArrayDataType();
      case INT:
        return SQLDataType.INTEGER;
      case INT_ARRAY:
        return SQLDataType.INTEGER.getArrayDataType();
      case BOOL:
        return SQLDataType.BOOLEAN;
      case BOOL_ARRAY:
        return SQLDataType.BOOLEAN.getArrayDataType();
      case DECIMAL:
        return SQLDataType.DOUBLE;
      case DECIMAL_ARRAY:
        return SQLDataType.DOUBLE.getArrayDataType();
      case TEXT:
        return SQLDataType.VARCHAR;
      case TEXT_ARRAY:
        return SQLDataType.VARCHAR.getArrayDataType();
      case DATE:
        return SQLDataType.DATE;
      case DATE_ARRAY:
        return SQLDataType.DATE.getArrayDataType();
      case DATETIME:
        return SQLDataType.TIMESTAMP;
      case DATETIME_ARRAY:
        return SQLDataType.TIMESTAMP.getArrayDataType();
      case JSONB:
        return SQLDataType.JSONB;
      default:
        // should never happen
        throw new IllegalArgumentException("jooqTypeOf(type) : unsupported type " + columnType);
    }
  }

  static Collection<Object> getValuesAsCollection(Row row, List<Column> columns) {
    Collection<Object> values = new ArrayList<>();
    for (Column c : columns) {
      Object value = getTypedValue(row, c.getName(), c.getColumnType());

      // validation
      if (value != null && c.getValidation() != null) {
        String error = validateValue(c.getValidation(), value);
        if (error != null)
          throw new MolgenisException(
              "Validation error on column '" + c.getName() + "'",
              error + ". Instead found value '" + value + "'");
      }
      // get value
      if (Constants.MG_EDIT_ROLE.equals(c.getName())) {
        values.add(Constants.MG_USER_PREFIX + row.getString(Constants.MG_EDIT_ROLE));
      } else {
        values.add(value);
      }
    }
    return values;
  }

  //  static Object getTypedValue(Object v, Column column) {
  //    ColumnType columnType = column.getColumnType();
  //    if (REF.equals(columnType)) {
  //      columnType = getRefColumnType(column);
  //    } else if (REF_ARRAY.equals(columnType)
  //        || REFBACK.equals(columnType)) { // /MREF.equals(columnType) ||
  //      columnType = getArrayType(getRefColumnType(column));
  //    }
  //    return getTypedValue(v, columnType);
  //  }

  public static Object getTypedValue(Object v, ColumnType columnType) {
    switch (columnType) {
      case UUID:
        return TypeUtils.toUuid(v);
      case UUID_ARRAY:
        return TypeUtils.toUuidArray(v);
      case STRING:
        return TypeUtils.toString(v);
      case STRING_ARRAY:
        return TypeUtils.toStringArray(v);
      case BOOL:
        return TypeUtils.toBool(v);
      case BOOL_ARRAY:
        return TypeUtils.toBoolArray(v);
      case INT:
        return TypeUtils.toInt(v);
      case INT_ARRAY:
        return TypeUtils.toIntArray(v);
      case DECIMAL:
        return TypeUtils.toDecimal(v);
      case DECIMAL_ARRAY:
        return TypeUtils.toDecimalArray(v);
      case TEXT:
        return cast(TypeUtils.toText(v), SQLDataType.VARCHAR);
      case TEXT_ARRAY:
        return cast(TypeUtils.toTextArray(v), SQLDataType.VARCHAR.getArrayDataType());
      case DATE:
        return TypeUtils.toDate(v);
      case DATE_ARRAY:
        return TypeUtils.toDateArray(v);
      case DATETIME:
        return TypeUtils.toDateTime(v);
      case DATETIME_ARRAY:
        return TypeUtils.toDateTimeArray(v);
      case JSONB:
        return TypeUtils.toJsonb(v);
      default:
        throw new UnsupportedOperationException(
            "Unsupported columnType columnType found:" + columnType);
    }
  }

  static Object getTypedValue(Row row, String name, ColumnType type) {
    switch (type) {
      case UUID:
        return row.getUuid(name);
      case UUID_ARRAY:
        return row.getUuidArray(name);
      case STRING:
        return row.getString(name);
      case STRING_ARRAY:
        return row.getStringArray(name);
      case BOOL:
        return row.getBoolean(name);
      case BOOL_ARRAY:
        return row.getBooleanArray(name);
      case INT:
        return row.getInteger(name);
      case INT_ARRAY:
        return row.getIntegerArray(name);
      case DECIMAL:
        return row.getDecimal(name);
      case DECIMAL_ARRAY:
        return row.getDecimalArray(name);
      case TEXT:
        return row.getText(name);
      case TEXT_ARRAY:
        return row.getTextArray(name);
      case DATE:
        return row.getDate(name);
      case DATE_ARRAY:
        return row.getDateArray(name);
      case DATETIME:
        return row.getDateTime(name);
      case DATETIME_ARRAY:
        return row.getDateTimeArray(name);
      case JSONB:
        return row.getJsonb(name);
      case JSONB_ARRAY:
        return row.getJsonbArray(name);
      default:
        throw new UnsupportedOperationException("Unsupported columnType found:" + type);
    }
  }

  //  static ColumnType getRefArrayColumnType(Column column) {
  //    return getArrayType(getRefColumnType(column));
  //  }

  //  public static ColumnType getRefColumnType(Column column) {
  //    ColumnType columnType;
  //    Column refColumn = column.getRefColumn();
  //    while (REF.equals(refColumn.getColumnType()) || REF_ARRAY.equals(refColumn.getColumnType()))
  // {
  //      refColumn = refColumn.getRefColumn();
  //      // check self reference
  //      if (refColumn.getTableName().equals(column.getTableName())
  //          && refColumn.getName().equals(column.getName())) {
  //        return STRING;
  //      }
  //    }
  //    columnType = refColumn.getColumnType();
  //    return columnType;
  //  }

  static TableMetadata getRefTable(Column column) {
    return column.getTable().getSchema().getTableMetadata(column.getRefTableName());
  }

  static String getPsqlType(Column column) {
    return getPsqlType(getPrimitiveColumnType(column));
  }

  static String getPsqlType(ColumnType type) {
    switch (type) {
      case STRING:
        return "character varying";
      case STRING_ARRAY:
        return "character varying[]";
      case UUID:
        return "uuid";
      case UUID_ARRAY:
        return "uuid[]";
      case BOOL:
        return "bool";
      case BOOL_ARRAY:
        return "bool[]";
      case INT:
        return "int";
      case INT_ARRAY:
        return "int[]";
      case DECIMAL:
        return "decimal";
      case DECIMAL_ARRAY:
        return "decimal[]";
      case TEXT:
        return "character varying";
      case TEXT_ARRAY:
        return "character varying[]";
      case DATE:
        return "date";
      case DATE_ARRAY:
        return "date[]";
      case DATETIME:
        return "timestamp without time zone";
      case DATETIME_ARRAY:
        return "timestamp without time zone[]";
      case JSONB:
        return "jsonb";
      default:
        throw new MolgenisException(
            "Unknown type", "Internal error: data cannot be mapped to psqlType " + type);
    }
  }
}
