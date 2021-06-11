package org.molgenis.emx2.sql;

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.molgenis.emx2.*;
import org.molgenis.emx2.Database;
import org.molgenis.emx2.Query;
import org.molgenis.emx2.Schema;
import org.molgenis.emx2.examples.CrossSchemaReferenceExample;

public class TestCrossSchemaForeignKeysAndInheritance {
  static Schema schema1;
  static Schema schema2;
  static Database db;

  @BeforeClass
  public static void setUp() {
    db = TestDatabaseFactory.getTestDatabase();
    schema1 =
        db.dropCreateSchema(TestCrossSchemaForeignKeysAndInheritance.class.getSimpleName() + "1");
    schema2 =
        db.dropCreateSchema(TestCrossSchemaForeignKeysAndInheritance.class.getSimpleName() + "2");

    CrossSchemaReferenceExample.create(schema1, schema2);
  }

  @Test
  public void testRef() {
    Query q =
        schema2
            .getTable("Child")
            .select(
                SelectColumn.s("name"),
                SelectColumn.s("parent", SelectColumn.s("name"), SelectColumn.s("hobby")));
    assertTrue(q.retrieveJSON().contains("stamps"));
    Assert.assertEquals("stamps", q.retrieveRows().get(0).getString("parent-hobby"));
  }

  @Test
  public void testRefArray() {
    Query q =
        schema2
            .getTable("PetLover")
            .select(
                SelectColumn.s("name"),
                SelectColumn.s("pets", SelectColumn.s("name"), SelectColumn.s("species")));
    Assert.assertEquals("dog", q.retrieveRows().get(1).getString("pets-species"));

    System.out.println(q.retrieveJSON());
    assertTrue(q.retrieveJSON().contains("dog"));
  }

  @Test
  public void testInheritance() {
    Query q = schema2.getTable("Mouse").select(SelectColumn.s("name"), SelectColumn.s("species"));
    Assert.assertEquals("mickey", q.retrieveRows().get(0).getString("name"));

    q = schema1.getTable("Pet").select(SelectColumn.s("name"), SelectColumn.s("species"));
    Assert.assertEquals(3, q.retrieveRows().size());
  }
}
