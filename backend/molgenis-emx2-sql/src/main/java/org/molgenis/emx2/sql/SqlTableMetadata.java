package org.molgenis.emx2.sql;

import org.jooq.DSLContext;
import org.molgenis.emx2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.*;
import static org.molgenis.emx2.Column.column;
import static org.molgenis.emx2.ColumnType.REF_ARRAY;
import static org.molgenis.emx2.sql.Constants.MG_EDIT_ROLE;
import static org.molgenis.emx2.sql.MetadataUtils.deleteColumn;
import static org.molgenis.emx2.sql.MetadataUtils.saveColumnMetadata;
import static org.molgenis.emx2.sql.SqlColumnExecutor.*;
import static org.molgenis.emx2.sql.SqlTableMetadataExecutor.*;

class SqlTableMetadata extends TableMetadata {
  private static final String SET_INHERITANCE_FAILED = "Set inheritance failed";
  private Database db;
  private static Logger logger = LoggerFactory.getLogger(SqlTableMetadata.class);

  SqlTableMetadata(Database db, SqlSchemaMetadata schema, TableMetadata metadata) {
    super(schema, metadata);
    this.db = db;
  }

  @Override
  public TableMetadata add(Column column) {
    long start = System.currentTimeMillis();
    db.tx(
        dsl -> {
          if (column.getName() == null) {
            throw new MolgenisException("Add column failed", "Column name cannot be null");
          }
          if (getColumn(column.getName()) != null) {
            alterColumn(column);
          } else {
            Column result = new Column(this, column);
            updatePositions(result, this);
            executeCreateColumn(getJooq(), result);
            super.add(result);
            if (column.getKey() > 0) {
              if (getKeyNames(column.getKey()).size() > 1 && column.isNullable()) {
                throw new MolgenisException(
                    "unique on column '" + column.getName() + "' failed",
                    "When key spans multiple columns, none of the columns can be nullable");
              }
              SqlTableMetadataExecutor.createOrReplaceKey(
                  getJooq(), this, column.getKey(), getKeyNames(column.getKey()));
            }
            SqlColumnExecutor.executeCreateRefAndNotNullConstraints(getJooq(), result);
            log(start, "added column '" + column.getName() + "' to ");
          }
        });
    db.getListener().schemaChanged(getSchemaName());
    return this;
  }

  @Override
  public TableMetadata alterColumn(String name, Column column) {
    Column oldColumn = getColumn(name);
    if (oldColumn == null) {
      throw new MolgenisException(
          "Alter column failed",
          "Column  '" + getTableName() + "." + column.getName() + "' does not exist");
    }
    db.tx(
        dsl -> {
          Column newColumn = new Column(this, column);

          // check if reference and of different size
          if (REF_ARRAY.equals(newColumn.getColumnType())
              && newColumn.getRefTable().getPrimaryKeyFields().size() > 1) {
            throw new MolgenisException(
                "Alter column of '" + oldColumn.getName() + " failed",
                "REF_ARRAY is not supported for composite keys of table "
                    + newColumn.getRefTableName());
          }
          if ((oldColumn.getReferences().size() > 1 || newColumn.getReferences().size() > 1)
              && oldColumn.getReferences().size() != newColumn.getReferences().size()) {
            throw new MolgenisException(
                "Cannot alter column '" + oldColumn.getName(),
                "New column '"
                    + newColumn.getName()
                    + "' has different number of reference multiplicity then '"
                    + oldColumn.getName()
                    + "'");
          }

          // change positions if needed
          if (!oldColumn.getPosition().equals(newColumn.getPosition())) {
            updatePositions(newColumn, this);
          }

          // drop old key, if touched
          if (oldColumn.getKey() > 0 && newColumn.getKey() != oldColumn.getKey()) {
            if (getKeyNames(newColumn.getKey()).size() > 1 && newColumn.isNullable()) {
              throw new MolgenisException(
                  "unique on column '" + newColumn.getName() + "' failed",
                  "When key spans multiple columns, none of the columns can be nullable");
            }
            executeDropKey(getJooq(), oldColumn.getTable(), oldColumn.getKey());
          }

          // drop referential constraints around this column
          executeRemoveRefAndNotNullConstraints(getJooq(), oldColumn);

          // remove refbacks if exist
          executeRemoveRefback(oldColumn, newColumn);

          // rename and retype if needed
          executeAlterType(getJooq(), oldColumn, newColumn);
          executeAlterName(getJooq(), oldColumn, newColumn);

          // (re)apply foreign keys
          executeCreateRefAndNotNullConstraints(getJooq(), newColumn);

          // change nullable?
          if (oldColumn.isNullable() != newColumn.isNullable()) {
            executeSetNullable(getJooq(), newColumn);
          }

          // update the metadata so we can use it for new keys and references
          super.alterColumn(name, newColumn);

          // check if refback constraints need updating
          reapplyRefbackContraints(oldColumn, newColumn);

          // create/update key, if touched
          if (newColumn.getKey() != oldColumn.getKey()) {

            createOrReplaceKey(
                getJooq(), this, newColumn.getKey(), getKeyNames(newColumn.getKey()));
          }

          // delete old column if name changed, then save any other metadata changes
          if (!oldColumn.getName().equals(newColumn.getName())) deleteColumn(getJooq(), oldColumn);
          saveColumnMetadata(getJooq(), newColumn);
        });
    db.getListener().schemaChanged(getSchemaName());

    return this;
  }

  @Override
  public void dropColumn(String name) {
    long start = System.currentTimeMillis();
    if (getColumn(name) == null) return; // return silently, idempotent
    db.tx(
        dsl -> {
          SqlColumnExecutor.executeRemoveColumn(getJooq(), getColumn(name));
          super.columns.remove(name);
        });
    db.getListener().schemaChanged(getSchemaName());
    log(start, "removed column '" + name + "' from ");
  }

  @Override
  public TableMetadata setInherit(String otherTable) {
    long start = System.currentTimeMillis();
    if (getInherit() != null) {
      if (getInherit().equals(otherTable)) {
        return this; // nothing to do
      } else {
        throw new MolgenisException(
            SET_INHERITANCE_FAILED,
            "Table '"
                + getTableName()
                + "'can only extend one table. Therefore it cannot extend '"
                + otherTable
                + "' because it already extends other table '"
                + getInherit()
                + "'");
      }
    }
    TableMetadata other = getSchema().getTableMetadata(otherTable);
    if (other == null)
      throw new MolgenisException(
          SET_INHERITANCE_FAILED, "Other table '" + otherTable + "' does not exist in this schema");

    if (other.getPrimaryKeys() == null)
      throw new MolgenisException(
          SET_INHERITANCE_FAILED,
          "To extend table '" + otherTable + "' it must have primary key set");
    db.tx(
        tdb -> {
          // extends means we copy primary key column from parent to child, make it foreign key to
          // parent, and make it primary key of this table also.
          executeSetInherit(getJooq(), this, other);
          super.setInherit(otherTable);
          MetadataUtils.saveTableMetadata(getJooq(), this);
        });
    db.getListener().schemaChanged(getSchemaName());
    log(start, "set inherit on ");
    return this;
  }

  @Override
  public TableMetadata removeInherit() {
    throw new MolgenisException("removeInherit not yet implemented", "");
  }

  @Override
  public SqlTableMetadata setSettings(Map<String, String> settings) {
    super.setSettings(settings);
    MetadataUtils.saveTableMetadata(getJooq(), this);
    db.getListener().schemaChanged(getSchemaName());
    return this;
  }

  @Override
  public void enableRowLevelSecurity() {
    this.add(column(MG_EDIT_ROLE).index(true));

    getJooq().execute("ALTER TABLE {0} ENABLE ROW LEVEL SECURITY", getJooqTable());
    getJooq()
        .execute(
            "CREATE POLICY {0} ON {1} USING (pg_has_role(session_user, {2}, 'member')) WITH CHECK (pg_has_role(session_user, {2}, 'member'))",
            name("RLS/" + getSchema().getName() + "/" + getTableName()),
            getJooqTable(),
            name(MG_EDIT_ROLE));
    // set RLS on the table
    // add policy for 'viewer' and 'editor'.
  }

  @Override
  public boolean exists() {
    // first look at already loaded metadata, in case of no columns, check the underlying table
    if (!getColumns().isEmpty()) {
      return true;
    }
    // jooq doesn't have operator for this, so by hand. Might be slow
    return 0
        < getJooq()
            .select(count())
            .from(name("information_schema", "tables"))
            .where(
                field("table_schema")
                    .eq(getSchema().getName())
                    .and(field("table_name").eq(getTableName())))
            .fetchOne(0, Integer.class);
  }

  public DSLContext getJooq() {
    return ((SqlDatabase) db).getJooq();
  }

  private void log(long start, String message) {
    String user = db.getActiveUser();
    if (user == null) user = "molgenis";
    if (logger.isInfoEnabled()) {
      logger.info(
          "{} {} {} in {}ms", user, message, getJooqTable(), (System.currentTimeMillis() - start));
    }
  }
}
