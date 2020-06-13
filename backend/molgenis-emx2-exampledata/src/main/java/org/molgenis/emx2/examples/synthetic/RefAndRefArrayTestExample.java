package org.molgenis.emx2.examples.synthetic;

import org.molgenis.emx2.ColumnType;
import org.molgenis.emx2.SchemaMetadata;

import static org.molgenis.emx2.Column.column;
import static org.molgenis.emx2.ColumnType.*;
import static org.molgenis.emx2.TableMetadata.table;

public class RefAndRefArrayTestExample {
  private RefAndRefArrayTestExample() {
    // hide constructor
  }

  public static void createRefAndRefArrayTestExample(SchemaMetadata schema) {

    ColumnType[] columnTypes =
        new ColumnType[] {UUID, STRING, BOOL, INT, DECIMAL, TEXT, DATE, DATETIME};

    for (ColumnType columnType : columnTypes) {

      String aTableName = columnType.toString() + "_A";
      String fieldName = "AKeyOf" + columnType;
      schema.create(table(aTableName).add(column(fieldName).type(columnType)).pkey(fieldName));

      String bTableName = columnType.toString() + "_B";
      String refFromBToA = "RefToAKeyOf" + columnType;
      String refArrayFromBToA = "RefArrayToAKeyOf" + columnType;

      schema.create(
          table(bTableName)
              .add(column(refFromBToA).type(REF).refTable(aTableName))
              .add(column(refArrayFromBToA).type(REF_ARRAY).refTable(aTableName)));
    }
  }
}
