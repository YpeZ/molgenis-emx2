package org.molgenis.emx2.sql;

import org.junit.Before;
import org.junit.Test;
import org.molgenis.emx2.Database;
import org.molgenis.emx2.Row;
import org.molgenis.emx2.Schema;
import org.molgenis.emx2.Table;

import java.util.List;

import static graphql.Assert.assertTrue;
import static org.junit.Assert.*;
import static org.molgenis.emx2.Column.column;
import static org.molgenis.emx2.ColumnType.*;
import static org.molgenis.emx2.FilterBean.*;
import static org.molgenis.emx2.Operator.EQUALS;
import static org.molgenis.emx2.SelectColumn.s;
import static org.molgenis.emx2.TableMetadata.table;

public class TestCompositeForeignKeys {
  private Database database;

  @Before
  public void setUp() {
    database = TestDatabaseFactory.getTestDatabase();

    // create target table

  }

  @Test
  public void testCompositeRef() {
    Schema schema =
        database.dropCreateSchema(TestCompositeForeignKeys.class.getSimpleName() + "Ref");

    schema.create(
        table(
            "Person",
            column("firstName").pkey(),
            column("lastName").pkey(),
            column("uncle", REF).refTable("Person").nullable(true)));

    Table p = schema.getTable("Person");

    p.insert(new Row().setString("firstName", "Donald").setString("lastName", "Duck"));

    try {
      p.insert(
          new Row()
              .setString("firstName", "Kwik")
              .setString("lastName", "Duck")
              .setString("uncle-firstName", "Donald")
              .setString("uncle-lastName", "MISSING"));
      fail("should have failed on missing foreign key");
    } catch (Exception e) {
      System.out.println("errored correctly: " + e);
    }

    p.insert(
        new Row()
            .setString("firstName", "Kwik")
            .setString("lastName", "Duck")
            .setString("uncle-firstName", "Donald")
            .setString("uncle-lastName", "Duck"));

    p.insert(
        new Row()
            .setString("firstName", "Kwek")
            .setString("lastName", "Duck")
            .setString("uncle-firstName", "Donald")
            .setString("uncle-lastName", "Duck"));

    p.insert(
        new Row()
            .setString("firstName", "Kwak")
            .setString("lastName", "Duck")
            .setString("uncle-firstName", "Donald")
            .setString("uncle-lastName", "Duck"));

    try {
      p.delete(new Row().setString("firstName", "Donald").setString("lastName", "Duck"));
      fail("should have failed on foreign key (Donald is used in foreign key)");
    } catch (Exception e) {
      System.out.println("errored correctly: " + e);
    }

    schema.create(table("Student").setInherit("Person"));
    Table s = schema.getTable("Student");
    s.insert(
        new Row()
            .setString("firstName", "Mickey")
            .setString("lastName", "Mouse")
            .setString("uncle-firstName", "Kwik")
            .setString("uncle-lastName", "Duck"));

    String result =
        schema
            .query(
                "Student",
                s("firstName"),
                s("lastName"),
                s("uncle", s("firstName"), s("lastName")),
                s("uncle", s("firstName"), s("lastName")))
            .retrieveJSON();
    System.out.println(result);

    result =
        schema
            .query("Student")
            .select(s("firstName"), s("lastName"))
            .where(
                or(
                    and(f("firstName", EQUALS, "Donald"), f("lastName", EQUALS, "Duck")),
                    and(f("firstName", EQUALS, "Mickey"), f("lastName", EQUALS, "Mouse"))))
            .retrieveJSON();

    System.out.println(result);
    assertTrue(result.contains("Mouse"));
    assertFalse(result.contains("Duck"));

    result =
        schema
            .query("Person")
            .select(s("firstName"), s("lastName"))
            .where(
                or(
                    and(f("firstName", EQUALS, "Donald"), f("lastName", EQUALS, "Duck")),
                    and(f("firstName", EQUALS, "Mickey"), f("lastName", EQUALS, "Mouse"))))
            .retrieveJSON();

    System.out.println(result);
    assertTrue(result.contains("Mouse"));
    assertTrue(result.contains("Duck"));

    // composite key filter
    result =
        schema
            .query("Person")
            .select(s("firstName"), s("lastName"))
            // composite filter, should result in 'donald duck' OR 'mickey mouse'
            .where(
                f(
                    "uncle",
                    or(
                        and(f("firstName", EQUALS, "Donald"), f("lastName", EQUALS, "Duck")),
                        and(f("firstName", EQUALS, "Mickey"), f("lastName", EQUALS, "Mouse")))))
            .retrieveJSON();

    System.out.println(result);
    assertTrue(result.contains("Kwik"));
    assertFalse(result.contains("Mouse"));

    // refback
    schema
        .getTable("Person")
        .getMetadata()
        .add(column("nephew").type(REFBACK).refTable("Person").mappedBy("uncle"));

    s.insert(
        new Row()
            .setString("firstName", "Katrien")
            .setString("lastName", "Duck")
            .setString("nephew-firstName", "Kwik")
            .setString("nephew-lastName", "Duck")); // I know, not true

    assertTrue(
        List.of(
                s.query()
                    .select(
                        s("firstName"),
                        s("lastName"),
                        s("nephew", s("firstName"), s("lastName")),
                        s("uncle", s("firstName"), s("lastName")))
                    .where(f("firstName", EQUALS, "Katrien"))
                    .retrieveRows()
                    .get(0)
                    .getStringArray("nephew-firstName"))
            .contains("Kwik"));
    assertTrue(
        List.of(
                p.query()
                    .select(
                        s("firstName"),
                        s("lastName"),
                        s("nephew", s("firstName"), s("lastName")),
                        s("uncle", s("firstName"), s("lastName")))
                    .where(f("firstName", EQUALS, "Kwik"))
                    .retrieveRows()
                    .get(0)
                    .getStringArray("uncle-firstName"))
            .contains("Katrien"));
  }

  @Test
  public void testCompositeRefArray() {
    Schema schema =
        database.dropCreateSchema(TestCompositeForeignKeys.class.getSimpleName() + "RefArray");

    schema.create(
        table(
            "Person",
            column("firstName").pkey(),
            column("lastName").pkey(),
            column("cousins", REF_ARRAY)
                .refTable("Person")
                .nullable(true))); // .with("lastName", "lastName")));

    Table p = schema.getTable("Person");

    p.insert(new Row().setString("firstName", "Kwik").setString("lastName", "Duck"));

    p.insert(
        new Row()
            .setString("firstName", "Donald")
            .setString("lastName", "Duck")
            .setString("cousins-firstName", "Kwik")
            .setString("cousins-lastName", "Duck"));

    try {
      p.delete(new Row().setString("firstName", "Kwik").setString("lastName", "Duck"));
      fail("should have failed on foreign key error");
    } catch (Exception e) {
      System.out.println("errored correctly: " + e);
    }

    schema.create(table("Student").setInherit("Person"));
    p = schema.getTable("Student");
    p.insert(
        new Row()
            .setString("firstName", "Mickey")
            .setString("lastName", "Mouse")
            .setString("cousins-firstName", "Kwik")
            .setString("cousins-lastName", "Duck"));

    String result =
        schema
            .query("Student")
            .select(s("firstName"), s("lastName"), s("cousins", s("firstName"), s("lastName")))
            .retrieveJSON();

    System.out.println(result);
  }

  @Test
  public void testCompositeMref() {
    Schema schema =
        database.dropCreateSchema(TestCompositeForeignKeys.class.getSimpleName() + "Mref");

    schema.create(
        table(
            "Person2",
            column("firstName").pkey(),
            column("lastName").pkey(),
            column("father", REF).refTable("Person2"),
            column("mother", REF).refTable("Person2"),
            column("children", MREF).refTable("Person2"))); // .with("lastName", "lastName")));
  }

  @Test
  public void testCompositeRefWithLinkToOtherColumn() {
    Schema schema =
        database.dropCreateSchema(TestCompositeForeignKeys.class.getSimpleName() + "LinkedRef");

    schema.create(table("Collection", column("name").pkey()));
    schema.create(
        table("Table", column("name").pkey(), column("collection").refTable("Collection").pkey()));
    schema.create(
        table(
            "Variable",
            column("name").pkey(),
            column("collection").type(REF).refTable("Collection"),
            column("table").type(REF).refTable("Table").refConstraint("collection=collection")));

    schema.getTable("Collection").insert(new Row().set("name", "LifeCycle"));
    schema.getTable("Table").insert(new Row().set("name", "Table1").set("collection", "LifeCycle"));
    schema
        .getTable("Variable")
        .insert(
            new Row()
                .set("name", "Variable1")
                .set("collection", "LifeCycle")
                .set("table", "Table1"));

    try {
      schema
          .getTable("Variable")
          .insert(
              new Row()
                  .set("name", "Variable1")
                  .set("collection", "LifeCycle")
                  .set("table", "Table2"));
      fail("should have failed");
    } catch (Exception e) {
      System.out.println("Error correct: " + e.getMessage());
    }

    assertEquals(
        "Table1", schema.getTable("Variable").query().retrieveRows().get(0).getString("table"));
  }
}
