package org.molgenis.emx2.io;

import org.junit.BeforeClass;
import org.junit.Test;
import org.molgenis.emx2.Database;
import org.molgenis.emx2.Row;
import org.molgenis.emx2.Schema;
import org.molgenis.emx2.io.rowstore.TableStore;
import org.molgenis.emx2.io.rowstore.TableStoreForCsvFilesDirectory;
import org.molgenis.emx2.io.rowstore.TableStoreForCsvInZipFile;
import org.molgenis.emx2.sql.TestDatabaseFactory;

import java.io.File;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.molgenis.emx2.SelectColumn.s;

public class TestLegacyImport {
  static Database db;

  @BeforeClass
  public static void setup() {
    db = TestDatabaseFactory.getTestDatabase();
  }

  @Test
  public void testLegacyImportDirectory() {

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("bbmri-nl-complete").getFile());
    TableStoreForCsvFilesDirectory store = new TableStoreForCsvFilesDirectory(file.toPath(), ',');

    Schema schema = db.dropCreateSchema("testImportLegacyFormatDirectory");
    executeTest(store, schema);
  }

  @Test
  public void testLegacyImportZip() {

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("bbmri-nl-complete.zip").getFile());
    TableStore store = new TableStoreForCsvInZipFile(file.toPath(), ',');

    Schema schema = db.dropCreateSchema("testImportLegacyFormatZip");
    executeTest(store, schema);
  }

  private void executeTest(TableStore store, Schema schema) {
    SchemaImport.executeImport(store, schema);

    assertEquals(22, schema.getTableNames().size());

    System.out.println("search GrONingen");
    List<Row> rows =
        schema
            .getTable("biobanks")
            .select(
                s("name"),
                s("contact_person", s("full_name")),
                s("principal_investigators", s("full_name")),
                s("juristic_person", s("name")))
            .search("GrONingen")
            .retrieveRows();
    assertEquals(1, rows.size());
    assertEquals("UMCG Research Data and Biobanking Team", rows.get(0).getString("name"));

    System.out.println("search groningen");
    for (Row r : schema.getTable("biobanks").search("groningen").retrieveRows()) {
      System.out.println(r.getString("name"));
    }

    System.out.println("search rotterdam");
    for (Row r : schema.getTable("biobanks").search("rotterdam").retrieveRows()) {
      System.out.println(r.getString("name"));
    }
  }
}
