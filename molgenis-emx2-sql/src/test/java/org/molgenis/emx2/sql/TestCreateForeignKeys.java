package org.molgenis.emx2.sql;

import org.junit.BeforeClass;
import org.junit.Test;
import org.molgenis.emx2.Database;
import org.molgenis.emx2.Row;
import org.molgenis.emx2.Table;
import org.molgenis.emx2.ColumnType;
import org.molgenis.emx2.Schema;

import static junit.framework.TestCase.fail;
import static org.molgenis.emx2.ColumnType.*;

public class TestCreateForeignKeys {

  static Database db;

  @BeforeClass
  public static void setup() {
    db = TestDatabaseFactory.getTestDatabase();
  }

  @Test
  public void testInt() {
    executeTest(INT, 5, 6);
  }

  @Test
  public void testString() {
    executeTest(STRING, "test", "DependencyOrderOutsideTransactionFails");
  }

  @Test
  public void testDate() {
    executeTest(DATE, "2013-01-01", "2013-01-02");
  }

  @Test
  public void testDateTime() {
    executeTest(DATETIME, "2013-01-01T18:00:00", "2013-01-01T18:00:01");
  }

  @Test
  public void testDecimal() {
    executeTest(DECIMAL, 5.0, 6.0);
  }

  @Test
  public void testText() {
    executeTest(TEXT, "This is a hello world", "This is a hello back to you");
  }

  @Test
  public void testUUID() {
    executeTest(
        UUID, "f83133cc-aeaa-11e9-a2a3-2a2ae2dbcce4", "f83133cc-aeaa-11e9-a2a3-2a2ae2dbcce5");
  }

  private void executeTest(ColumnType columnType, Object insertValue, Object updateValue) {

    Schema schema = db.createSchema("TestCreateForeignKeys" + columnType.toString().toUpperCase());

    Table aTable = schema.createTableIfNotExists("A");
    String fieldName = "AKeyOf" + columnType;
    aTable
        .getMetadata()
        .addColumn("ID", ColumnType.INT)
        .primaryKey()
        .addColumn(fieldName, columnType)
        .addUnique(fieldName);
    Row aRow = new Row().setInt("ID", 1).set(fieldName, insertValue);
    aTable.insert(aRow);

    Table bTable = schema.createTableIfNotExists("B");
    String refFromBToA = "RefToAKeyOf" + columnType;
    bTable
        .getMetadata()
        .addColumn("ID", ColumnType.INT)
        .primaryKey()
        .addRef(refFromBToA, "A", fieldName);
    Row bRow = new Row().setInt("ID", 2).set(refFromBToA, insertValue);
    bTable.insert(bRow);

    // insert to non-existing value should fail
    Row bErrorRow = new Row().setInt("ID", 3).set(refFromBToA, updateValue);
    try {
      bTable.insert(bErrorRow);
      fail("insert should fail because value is missing");
    } catch (Exception e) {
      System.out.println("insert exception correct: \n" + e);
    }

    // and update, should be cascading :-)
    aTable.update(aRow.set(fieldName, updateValue));

    // delete of A should fail
    try {
      aTable.delete(aRow);
      fail("delete should fail because bRow is still referencing aRow");
    } catch (Exception e) {
      System.out.println("insert exception correct: \n" + e);
    }

    bTable.delete(bRow);
    aTable.delete(aRow);
  }
}
