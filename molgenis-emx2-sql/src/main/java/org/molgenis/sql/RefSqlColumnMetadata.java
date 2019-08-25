package org.molgenis.sql;

import org.jooq.Field;
import org.jooq.Name;
import org.molgenis.MolgenisException;

import static org.jooq.impl.DSL.*;
import static org.molgenis.metadata.Type.REF;
import static org.molgenis.sql.MetadataUtils.saveColumnMetadata;

public class RefSqlColumnMetadata extends SqlColumnMetadata {

  public RefSqlColumnMetadata(
      SqlTableMetadata table, String columnName, String toTable, String toColumn) {
    super(table, columnName, REF);
    this.setReference(toTable, toColumn);
  }

  @Override
  public RefSqlColumnMetadata createColumn() throws MolgenisException {

    // define jooq parameters
    Field thisColumn = field(name(getColumnName()), SqlTypeUtils.jooqTypeOf(this));
    org.jooq.Table thisTable =
        table(name(getTable().getSchema().getName(), getTable().getTableName()));
    Name fkeyConstraintName =
        name(
            getTable().getTableName()
                + "."
                + getColumnName()
                + " REFERENCES "
                + getRefTableName()
                + "."
                + getRefColumnName());
    Name fkeyTable = name(getTable().getSchema().getName(), getRefTableName());
    Name fkeyField = name(getRefColumnName());

    // execute alter table add column
    getJooq().alterTable(thisTable).addColumn(thisColumn).execute();

    getJooq()
        .alterTable(thisTable)
        .add(
            constraint(fkeyConstraintName)
                .foreignKey(thisColumn)
                .references(fkeyTable, fkeyField)
                .onUpdateCascade())
        .execute();

    getJooq()
        .execute(
            "ALTER TABLE {0} ALTER CONSTRAINT {1} DEFERRABLE INITIALLY IMMEDIATE",
            thisTable, fkeyConstraintName);

    getJooq()
        .createIndex(name(getTable().getTableName()) + "_" + name(getColumnName()) + "_FKINDEX")
        .on(thisTable, thisColumn)
        .execute();

    saveColumnMetadata(this);

    return this;
  }
}
