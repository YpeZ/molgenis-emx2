package org.molgenis.emx2.graphql;

import static org.junit.Assert.*;
import static org.molgenis.emx2.Column.column;
import static org.molgenis.emx2.ColumnType.REF;
import static org.molgenis.emx2.ColumnType.REF_ARRAY;
import static org.molgenis.emx2.Row.row;
import static org.molgenis.emx2.TableMetadata.table;
import static org.molgenis.emx2.graphql.GraphqlApiFactory.convertExecutionResultToJson;
import static org.molgenis.emx2.sql.SqlDatabase.ANONYMOUS;
import static org.molgenis.emx2.utils.TypeUtils.convertToCamelCase;
import static org.molgenis.emx2.utils.TypeUtils.convertToPascalCase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.GraphQL;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.junit.BeforeClass;
import org.junit.Test;
import org.molgenis.emx2.*;
import org.molgenis.emx2.datamodels.PetStoreLoader;
import org.molgenis.emx2.sql.TestDatabaseFactory;
import org.molgenis.emx2.tasks.Task;
import org.molgenis.emx2.tasks.TaskService;
import org.molgenis.emx2.tasks.TaskServiceInMemory;

public class TestGraphqSchemaFields {

  private static GraphQL grapql;
  private static Database database;
  private static final String schemaName = TestGraphqSchemaFields.class.getSimpleName();
  private static TaskService taskService;
  private static Schema schema;

  @BeforeClass
  public static void setup() {
    database = TestDatabaseFactory.getTestDatabase();
    schema = database.dropCreateSchema(schemaName);
    new PetStoreLoader().load(schema, true);
    taskService = new TaskServiceInMemory();
    grapql = new GraphqlApiFactory().createGraphqlForSchema(schema, taskService);
  }

  @Test
  public void testSession() throws IOException {
    try {
      database.setActiveUser(ANONYMOUS);
      TestCase.assertEquals(1, execute("{_session{email,roles}}").at("/_session/roles").size());
      execute("mutation { signin(email: \"shopmanager\",password:\"shopmanager\") {message}}");
      grapql =
          new GraphqlApiFactory()
              .createGraphqlForSchema(database.getSchema(schemaName), taskService);
      TestCase.assertTrue(execute("{_session{email,roles}}").toString().contains("Manager"));
    } finally {
      database.becomeAdmin();
    }
  }

  @Test
  public void testSchemaSettings() throws IOException {
    // add value
    execute("mutation{change(settings:{key:\"test\",value:\"testval\"}){message}}");

    assertEquals("testval", execute("{_settings{key,value}}").at("/_settings/1/value").textValue());

    // remove value
    execute("mutation{drop(settings:{key:\"test\"}){message}}");
  }

  @Test
  public void testFetchSchemaSettingsByKey() throws IOException {
    // add value
    execute("mutation{change(settings:{key:\"setA\",value:\"valA\"}){message}}");
    execute("mutation{change(settings:{key:\"setB\",value:\"valB\"}){message}}");

    // fetch by key
    assertEquals(2, execute("{_settings(keys: [\"setB\"]){key,value}}").at("/_settings").size());
    assertEquals(
        "valB",
        execute("{_settings(keys: [\"setB\"]){key,value}}").at("/_settings/0/value").textValue());

    // return all without key
    assertEquals(4, execute("{_settings{key,value}}").at("/_settings").size());

    // remove value
    execute("mutation{drop(settings:{key:\"setA\"}){message}}");
    execute("mutation{drop(settings:{key:\"setB\"}){message}}");
  }

  @Test
  public void testFetchSchemaSettingsForPages() throws IOException {
    // add value
    execute("mutation{change(settings:{key:\"page.mypage\",value:\"page value\"}){message}}");

    // include all pages
    assertEquals(
        "page value",
        execute("{_settings(keys: [\"page.\"]){key,value}}").at("/_settings/0/value").textValue());

    // remove value
    execute("mutation{drop(settings:{key:\"page.mypage\"}){message}}");
  }

  @Test
  public void testTableSettings() throws IOException {
    // add value
    execute(
        "mutation{change(tables:[{name:\"Pet\",settings:{key:\"test\",value:\"testval\"}}]){message}}");

    assertEquals(
        "testval",
        execute("{_schema{tables{settings{key,value}}}}")
            .at("/_schema/tables/2/settings/0/value")
            .textValue());

    // remove value
    execute("mutation{drop(settings:[{table:\"Pet\", key:\"test\"}]){message}}");

    assertEquals(0, schema.getTable("Pet").getMetadata().getSettings().size());
    assertEquals(
        0,
        execute("{_schema{tables{settings{key,value}}}}").at("/_schema/tables/2/settings").size());
  }

  @Test
  public void testTableQueries() throws IOException {
    // simple
    TestCase.assertEquals("pooky", execute("{Pet{name}}").at("/Pet/0/name").textValue());

    TestCase.assertEquals(
        "pooky", execute("{Pet{name}Pet_agg{count}}").at("/Pet/0/name").textValue());

    // simple ref
    JsonNode result = execute("{Pet{name,category{name}}}");
    TestCase.assertEquals("cat", result.at("/Pet/0/category/name").textValue());

    // equals text
    TestCase.assertEquals(
        "spike",
        execute("{Pet(filter:{name:{equals:\"spike\"}}){name}}").at("/Pet/0/name").textValue());

    // not equals text
    TestCase.assertEquals(
        "pooky",
        execute("{Pet(filter:{name:{not_equals:\"spike\"}}){name}}").at("/Pet/0/name").textValue());

    // like text
    TestCase.assertEquals(
        "pooky",
        execute("{Pet(filter:{name:{like:\"oky\"}}){name}}").at("/Pet/0/name").textValue());
    // not like text
    TestCase.assertEquals(
        "spike",
        execute("{Pet(filter:{name:{not_like:\"oky\"}}){name}}").at("/Pet/0/name").textValue());

    // trigram
    TestCase.assertEquals(
        "pooky",
        execute("{Pet(filter:{name:{trigram_search:\"pook\"}}){name}}")
            .at("/Pet/0/name")
            .textValue());

    // textsearch
    TestCase.assertEquals(
        "pooky",
        execute("{Pet(filter:{name:{text_search:\"pook\"}}){name}}").at("/Pet/0/name").textValue());

    // equals int
    TestCase.assertEquals(
        "pooky",
        execute("{Order(filter:{quantity:{equals:1}}){quantity,pet{name}}}")
            .at("/Order/0/pet/name")
            .textValue());

    // not equals int
    TestCase.assertEquals(
        "spike",
        execute("{Order(filter:{quantity:{not_equals:1}}){quantity,pet{name}}}")
            .at("/Order/0/pet/name")
            .textValue());

    // between int
    TestCase.assertEquals(
        "pooky",
        execute("{Order(filter:{quantity:{between:[1,3]}}){quantity,pet{name}}}")
            .at("/Order/0/pet/name")
            .textValue());

    // between int one sided
    TestCase.assertEquals(
        "spike",
        execute("{Order(filter:{quantity:{between:[3,null]}}){quantity,pet{name}}}")
            .at("/Order/0/pet/name")
            .textValue());

    // between int one sided
    TestCase.assertEquals(
        "pooky",
        execute("{Order(filter:{quantity:{not_between:[null,3]}}){quantity,pet{name}}}")
            .at("/Order/0/pet/name")
            .textValue());

    // equal bool
    TestCase.assertEquals(
        "pooky",
        execute("{Order(filter:{complete:{equals:true}}){quantity,pet{name}}}")
            .at("/Order/0/pet/name")
            .textValue());

    // not equal bool
    TestCase.assertEquals(
        "spike",
        execute("{Order(filter:{complete:{equals:false}}){quantity,pet{name}}}")
            .at("/Order/0/pet/name")
            .textValue());

    // search
    TestCase.assertEquals(
        "spike",
        execute("{Pet(search:\"spike\"){name,category{name},tags{name}}}")
            .at("/Pet/0/name")
            .textValue());

    // offset
    TestCase.assertEquals(
        "jerry", execute("{Pet(offset:1,orderby:{name:ASC}){name}}").at("/Pet/0/name").textValue());

    // limit
    TestCase.assertEquals(1, execute("{Pet(limit:1){name}}").at("/Pet").size());

    // orderby asc
    TestCase.assertEquals(
        "fire ant", execute("{Pet(orderby:{name:ASC}){name}}").at("/Pet/0/name").textValue());

    // order by desc
    TestCase.assertEquals(
        "tweety", execute("{Pet(orderby:{name:DESC}){name}}").at("/Pet/0/name").textValue());

    // order by on non-root column
    TestCase.assertEquals(
        "delivered",
        execute("{Pet {orders(orderby: {orderId: ASC}) {status}}}")
            .at("/Pet/0/orders/0/status")
            .textValue());

    // filter nested
    TestCase.assertEquals(
        "red",
        execute("{Pet(filter:{tags:{name:{equals:\"red\"}}}){name,tags{name}}}")
            .at("/Pet/0/tags/0/name")
            .textValue());

    // or
    TestCase.assertEquals(
        2,
        execute("{Pet(filter:{_or:[{name:{equals:\"pooky\"}},{name:{equals:\"spike\"}}]}){name}}")
            .at("/Pet")
            .size());

    // or nested
    TestCase.assertEquals(
        4,
        execute(
                "{Pet(filter:{_or:[{name:{equals:\"pooky\"}},{tags:{_or:[{name:{equals:\"green\"}}]}}]}){name}}")
            .at("/Pet")
            .size());

    // or nested
    TestCase.assertEquals(
        4,
        execute(
                "{Pet_agg(filter:{_or:[{name:{equals:\"pooky\"}},{tags:{_or:[{name:{equals:\"green\"}}]}}]}){count}}")
            .at("/Pet_agg/count")
            .intValue());

    // root agg
    TestCase.assertEquals(
        7,
        execute("{Order_agg{max{quantity},min{quantity},sum{quantity},avg{quantity}}}")
            .at("/Order_agg/max/quantity")
            .intValue());

    // nested agg
    TestCase.assertEquals(
        15.7d,
        execute("{User{pets_agg{count,max{weight},min{weight},sum{weight},avg{weight}}}}")
            .at("/User/0/pets_agg/max/weight")
            .doubleValue(),
        0.0f);

    // subfilters
    result =
        execute(
            "{Pet(filter:{name:{equals:\"spike\"}}){name,tags(filter:{name:{equals:\"green\"}}){name}}}");
    assertEquals("spike", result.at("/Pet/0/name").textValue());
    assertEquals("green", result.at("/Pet/0/tags/0/name").textValue());
    assertEquals(1, result.at("/Pet/0/tags").size());

    // nested orderby should give reasonable error
    try {
      execute("{Pet(filter:{name:{equals:\"spike\"}}){name,tags(orderby:{blaat:ASC}){name}}}");
      fail("should fail");
    } catch (MolgenisException e) {
      assertTrue(e.getMessage().contains("Validation error of type WrongType: argument 'orderby'"));
    }
  }

  @Test
  public void testGroupBy() throws IOException {
    // refs
    JsonNode result = execute("{Pet_groupBy{count,tags{name}}}");
    // 1 red
    TestCase.assertEquals(null, result.at("/Pet_groupBy/0/tags/name").textValue());
    TestCase.assertEquals(1, result.at("/Pet_groupBy/0/count").intValue());
    // 1 green
    TestCase.assertEquals("blue", result.at("/Pet_groupBy/1/tags/name").asText());
    TestCase.assertEquals(1, result.at("/Pet_groupBy/1/count").intValue());
    // 1 with no tags
    TestCase.assertEquals("green", result.at("/Pet_groupBy/2/tags/name").textValue());
    TestCase.assertEquals(3, result.at("/Pet_groupBy/2/count").intValue());

    result = execute("{Pet_groupBy{count,category{name}}}");
    TestCase.assertEquals(1, result.at("/Pet_groupBy/0/count").intValue());
    TestCase.assertEquals("ant", result.at("/Pet_groupBy/0/category/name").textValue());
    TestCase.assertEquals("bird", result.at("/Pet_groupBy/1/category/name").textValue());

    // currently doensn't contain cat because somehow 'null' are not included
    result = execute("{Pet_groupBy{count,tags{name},category{name}}}");
    // 1 <untagged> cat
    TestCase.assertEquals(1, result.at("/Pet_groupBy/0/count").intValue());
    TestCase.assertEquals("cat", result.at("/Pet_groupBy/0/category/name").textValue());
    TestCase.assertEquals(null, result.at("/Pet_groupBy/0/tags/name").textValue());
    // 1 blue mouse
    TestCase.assertEquals(1, result.at("/Pet_groupBy/1/count").intValue());
    TestCase.assertEquals("mouse", result.at("/Pet_groupBy/1/category/name").textValue());
    TestCase.assertEquals("blue", result.at("/Pet_groupBy/1/tags/name").textValue());
    // 1 green ant
    TestCase.assertEquals(1, result.at("/Pet_groupBy/2/count").intValue());
    TestCase.assertEquals("ant", result.at("/Pet_groupBy/2/category/name").textValue());
    TestCase.assertEquals("green", result.at("/Pet_groupBy/2/tags/name").textValue());

    // N.B. in case arrays are involved total might more than count!!!
  }

  @Test
  public void testGroupByWithSpaces() throws IOException {
    // rename column 'category' to 'category_test' and 'tag' to 'tag test' and 'name' to 'name test'
    Column newCategory = schema.getTable("Pet").getMetadata().getColumn("category");
    newCategory.setName("category test");
    Column newTags = schema.getTable("Pet").getMetadata().getColumn("tags");
    newTags.setName("tags test");
    Column newCategoryName = schema.getTable("Category").getMetadata().getColumn("name");
    newCategoryName.setName("name test");
    Column newTagName = schema.getTable("Tag").getMetadata().getColumn("name");
    newTagName.setName("name test");
    schema.getTable("Pet").getMetadata().alterColumn("category", newCategory);
    schema.getTable("Pet").getMetadata().alterColumn("tags", newTags);
    schema.getTable("Category").getMetadata().alterColumn("name", newCategoryName);
    schema.getTable("Tag").getMetadata().alterColumn("name", newTagName);

    // refresh graphql
    grapql =
        new GraphqlApiFactory().createGraphqlForSchema(database.getSchema(schemaName), taskService);

    // refs
    JsonNode result = execute("{Pet_groupBy{count,tagsTest{nameTest}}}");
    // 1 red
    TestCase.assertEquals(null, result.at("/Pet_groupBy/0/tagsTest/nameTest").textValue());
    TestCase.assertEquals(1, result.at("/Pet_groupBy/0/count").intValue());
    // 1 green
    TestCase.assertEquals("blue", result.at("/Pet_groupBy/1/tagsTest/nameTest").asText());
    TestCase.assertEquals(1, result.at("/Pet_groupBy/1/count").intValue());
    // 1 with no tags
    TestCase.assertEquals("green", result.at("/Pet_groupBy/2/tagsTest/nameTest").textValue());
    TestCase.assertEquals(3, result.at("/Pet_groupBy/2/count").intValue());

    result = execute("{Pet_groupBy{count,categoryTest{nameTest}}}");
    TestCase.assertEquals(1, result.at("/Pet_groupBy/0/count").intValue());
    TestCase.assertEquals("ant", result.at("/Pet_groupBy/0/categoryTest/nameTest").textValue());
    TestCase.assertEquals("bird", result.at("/Pet_groupBy/1/categoryTest/nameTest").textValue());

    // currently doensn't contain cat because somehow 'null' are not included
    result = execute("{Pet_groupBy{count,tagsTest{nameTest},categoryTest{nameTest}}}");
    // 1 <untagged> cat
    TestCase.assertEquals(1, result.at("/Pet_groupBy/0/count").intValue());
    TestCase.assertEquals("cat", result.at("/Pet_groupBy/0/categoryTest/nameTest").textValue());
    TestCase.assertEquals(null, result.at("/Pet_groupBy/0/tagsTest/nameTest").textValue());
    // 1 blue mouse
    TestCase.assertEquals(1, result.at("/Pet_groupBy/1/count").intValue());
    TestCase.assertEquals("mouse", result.at("/Pet_groupBy/1/categoryTest/nameTest").textValue());
    TestCase.assertEquals("blue", result.at("/Pet_groupBy/1/tagsTest/nameTest").textValue());
    // 1 green ant
    TestCase.assertEquals(1, result.at("/Pet_groupBy/2/count").intValue());
    TestCase.assertEquals("ant", result.at("/Pet_groupBy/2/categoryTest/nameTest").textValue());
    TestCase.assertEquals("green", result.at("/Pet_groupBy/2/tagsTest/nameTest").textValue());

    // N.B. in case arrays are involved total might more than count!!!

    // undo rename column with spaces for any other test
    newCategory = schema.getTable("Pet").getMetadata().getColumn("category test");
    newCategory.setName("category");
    newTags = schema.getTable("Pet").getMetadata().getColumn("tags test");
    newTags.setName("tags");
    newCategoryName = schema.getTable("Category").getMetadata().getColumn("name test");
    newCategoryName.setName("name");
    newTagName = schema.getTable("Tag").getMetadata().getColumn("name test");
    newTagName.setName("name");
    schema.getTable("Pet").getMetadata().alterColumn("category test", newCategory);
    schema.getTable("Pet").getMetadata().alterColumn("tags test", newTags);
    schema.getTable("Category").getMetadata().alterColumn("name test", newCategoryName);
    schema.getTable("Tag").getMetadata().alterColumn("name test", newTagName);
    // refresh graphql
    grapql =
        new GraphqlApiFactory().createGraphqlForSchema(database.getSchema(schemaName), taskService);
  }

  @Test
  public void testSchemaQueries() throws IOException {
    TestCase.assertEquals(schemaName, execute("{_schema{name}}").at("/_schema/name").textValue());
  }

  @Test
  public void testMembersOperations() throws IOException {

    // list members
    int count = execute("{_schema{members{email}}}").at("/_schema/members").size();

    // add members
    execute("mutation{change(members:{email:\"blaat\", role:\"Manager\"}){message}}");
    TestCase.assertEquals(
        count + 1, execute("{_schema{members{email}}}").at("/_schema/members").size());

    // remove members
    execute("mutation{drop(members:\"blaat\"){message}}");
    TestCase.assertEquals(
        count, execute("{_schema{members{email}}}").at("/_schema/members").size());
  }

  @Test
  public void testTableAlterDropOperations() throws IOException {
    // simple meta
    TestCase.assertEquals(5, execute("{_schema{tables{name}}}").at("/_schema/tables").size());

    // add table
    execute(
        "mutation{change(tables:[{name:\"table1\",labels:[{locale:\"en\", value: \"table1\"}],descriptions:[{locale:\"en\", value: \"desc1\"}],columns:[{name:\"col1\", key:1, labels:[{locale:\"en\", value:\"column1\"}], descriptions:[{locale:\"en\", value:\"desc11\"}]}]}]){message}}");

    JsonNode node =
        execute(
            "{_schema{tables{name,labels{locale,value},descriptions{locale,value},columns{name,key,labels{locale,value},descriptions{locale,value}}}}}");
    TestCase.assertEquals(1, node.at("/_schema/tables/0/columns/0/key").intValue());

    TestCase.assertEquals("en", node.at("/_schema/tables/5/labels/0/locale").asText());
    TestCase.assertEquals("table1", node.at("/_schema/tables/5/labels/0/value").asText());

    TestCase.assertEquals("en", node.at("/_schema/tables/5/descriptions/0/locale").asText());
    TestCase.assertEquals("desc1", node.at("/_schema/tables/5/descriptions/0/value").asText());

    TestCase.assertEquals("en", node.at("/_schema/tables/5/columns/0/labels/0/locale").asText());
    TestCase.assertEquals(
        "column1", node.at("/_schema/tables/5/columns/0/labels/0/value").asText());

    TestCase.assertEquals(
        "en", node.at("/_schema/tables/5/columns/0/descriptions/0/locale").asText());
    TestCase.assertEquals(
        "desc11", node.at("/_schema/tables/5/columns/0/descriptions/0/value").asText());

    TestCase.assertEquals(6, execute("{_schema{tables{name}}}").at("/_schema/tables").size());

    // drop
    execute("mutation{drop(tables:\"table1\"){message}}");
    TestCase.assertEquals(5, execute("{_schema{tables{name}}}").at("/_schema/tables").size());
  }

  private JsonNode execute(String query) throws IOException {
    String result = convertExecutionResultToJson(grapql.execute(query));
    JsonNode node = new ObjectMapper().readTree(result);
    if (node.get("errors") != null) {
      throw new MolgenisException(node.get("errors").get(0).get("message").asText());
    }
    return new ObjectMapper().readTree(result).get("data");
  }

  @Test
  public void saveAndDeleteRows() throws IOException {
    int count = execute("{Tag_agg{count}}").at("/Tag_agg/count").intValue();
    // insert should increase count
    execute("mutation{insert(Tag:{name:\"blaat\"}){message}}");
    TestCase.assertEquals(count + 1, execute("{Tag_agg{count}}").at("/Tag_agg/count").intValue());
    // delete
    execute("mutation{delete(Tag:{name:\"blaat\"}){message}}");
    TestCase.assertEquals(count, execute("{Tag_agg{count}}").at("/Tag_agg/count").intValue());
  }

  @Test
  public void testAddAlterDropColumn() throws IOException {
    execute("mutation{change(columns:{table:\"Pet\",name:\"test\"}){message}}");
    TestCase.assertNotNull(
        database.getSchema(schemaName).getTable("Pet").getMetadata().getColumn("test"));
    execute(
        "mutation{change(columns:{table:\"Pet\", oldName:\"test\",name:\"test2\", key:3, columnType:\"INT\"}){message}}");

    database.clearCache(); // cannot know here, server clears caches

    assertNull(database.getSchema(schemaName).getTable("Pet").getMetadata().getColumn("test"));
    TestCase.assertEquals(
        ColumnType.INT,
        database
            .getSchema(schemaName)
            .getTable("Pet")
            .getMetadata()
            .getColumn("test2")
            .getColumnType());

    execute("mutation{drop(columns:[{table:\"Pet\", column:\"test2\"}]){message}}");

    database.clearCache(); // cannot know here, server clears caches

    assertNull(database.getSchema(schemaName).getTable("Pet").getMetadata().getColumn("test2"));

    execute(
        "mutation{change(columns:{table:\"Pet\", name:\"test2\", columnType:\"STRING\", visible:\"blaat\"}){message}}");
    database.clearCache(); // cannot know here, server clears caches
    assertEquals(
        "blaat",
        database
            .getSchema(schemaName)
            .getTable("Pet")
            .getMetadata()
            .getColumn("test2")
            .getVisible());
    execute("mutation{drop(columns:[{table:\"Pet\", column:\"test2\"}]){message}}");

    execute(
        "mutation{change(columns:{table:\"Pet\", name:\"test2\", columnType:\"STRING\", computed:\"blaat2\"}){message}}");
    database.clearCache(); // cannot know here, server clears caches
    assertEquals(
        "blaat2",
        database
            .getSchema(schemaName)
            .getTable("Pet")
            .getMetadata()
            .getColumn("test2")
            .getComputed());
    execute("mutation{drop(columns:[{table:\"Pet\", column:\"test2\"}]){message}}");
  }

  @Test
  public void testNamesWithSpaces() throws IOException {
    try {
      Schema myschema = database.dropCreateSchema("testNamesWithSpaces");

      // test escaping
      assertEquals("firstName", convertToCamelCase("First name"));
      assertEquals("firstName", convertToCamelCase("First  name"));
      assertEquals("first_name", convertToCamelCase("first_name"));

      assertEquals("FirstName", convertToPascalCase("first name"));
      assertEquals("FirstName", convertToPascalCase("first  name"));
      assertEquals("First_name", convertToPascalCase("first_name"));

      System.out.println(convertToCamelCase("Person details"));

      myschema.create(
          table(
              "Person details",
              column("First name").setPkey(),
              column("Last_name").setPkey(),
              column("some number").setType(ColumnType.INT)),
          table(
              "Some",
              column("id").setPkey(),
              column("person").setType(REF).setRefTable("Person details"),
              column("persons").setType(REF_ARRAY).setRefTable("Person details")));

      grapql = new GraphqlApiFactory().createGraphqlForSchema(myschema, taskService);
      execute(
          "mutation{insert(PersonDetails:{firstName:\"blaata\",last_name:\"blaata2\",someNumber: 6}){message}}");

      int count = execute("{PersonDetails_agg{count}}").at("/PersonDetails_agg/count").intValue();

      // insert should increase count
      execute(
          "mutation{insert(PersonDetails:{firstName:\"blaatb\",last_name:\"blaatb2\"}){message}}");
      TestCase.assertEquals(
          count + 1,
          execute("{PersonDetails_agg{count}}").at("/PersonDetails_agg/count").intValue());

      // order by should work with spaces
      TestCase.assertEquals(
          "blaata",
          execute("{PersonDetails(orderby:{firstName:ASC}){firstName}}")
              .at("/PersonDetails/0/firstName")
              .asText());

      TestCase.assertEquals(
          "blaatb",
          execute("{PersonDetails(orderby:{firstName:DESC}){firstName}}")
              .at("/PersonDetails/0/firstName")
              .asText());

      // order by should work with underscore
      TestCase.assertEquals(
          "blaata2",
          execute("{PersonDetails(orderby:{last_name:ASC}){last_name}}")
              .at("/PersonDetails/0/last_name")
              .asText());

      TestCase.assertEquals(
          "blaatb2",
          execute("{PersonDetails(orderby:{last_name:DESC}){last_name}}")
              .at("/PersonDetails/0/last_name")
              .asText());

      // aggregates should be working with spaces too
      JsonNode agg =
          execute(
              "{PersonDetails_agg{sum{someNumber}avg{someNumber}min{someNumber}max{someNumber}}}");
      TestCase.assertEquals(6, agg.at("/PersonDetails_agg/sum/someNumber").asInt());
      TestCase.assertEquals(6, agg.at("/PersonDetails_agg/avg/someNumber").asInt());
      TestCase.assertEquals(6, agg.at("/PersonDetails_agg/min/someNumber").asInt());
      TestCase.assertEquals(6, agg.at("/PersonDetails_agg/max/someNumber").asInt());

      // delete
      execute(
          "mutation{delete(PersonDetails:{firstName:\"blaata\",last_name:\"blaata2\"}){message}}");
      TestCase.assertEquals(
          count, execute("{PersonDetails_agg{count}}").at("/PersonDetails_agg/count").intValue());

      // reset
    } finally {
      grapql = new GraphqlApiFactory().createGraphqlForSchema(schema, taskService);
    }
  }

  @Test
  public void testTableType() throws IOException {
    JsonNode result = execute("{_schema{name,tables{name,tableType}}}");
    assertEquals("DATA", result.at("/_schema/tables/0/tableType").asText(), "DATA");
    assertEquals("ONTOLOGIES", result.at("/_schema/tables/3/tableType").asText());
  }

  @Test
  public void testFileType() throws IOException {
    try {
      Schema myschema = database.dropCreateSchema("testFileType");
      myschema.create(
          table("TestFile", column("name").setPkey(), column("image").setType(ColumnType.FILE)));

      grapql = new GraphqlApiFactory().createGraphqlForSchema(myschema, taskService);

      // insert file (note: ideally here also use mutation but I don't know how to add file part to
      // request)
      Table table = myschema.getTable("TestFile");
      table.insert(
          row(
              "name",
              "test",
              "image",
              new BinaryFileWrapper("text/html", "testfile.txt", "test".getBytes())));

      assertEquals(4, execute("{TestFile{image{size}}}").at("/TestFile/0/image/size").asInt());

      // update with {} existing file metadata should keep file untouched
      Map data = new LinkedHashMap();
      data.put("name", "test");
      data.put("image", Map.of("name", "dummy"));
      grapql.execute(
          new ExecutionInput.Builder()
              .query("mutation update($value:[TestFileInput]){update(TestFile:$value){message}}")
              .variables(Map.of("value", data))
              .build());
      assertEquals(4, execute("{TestFile{image{size}}}").at("/TestFile/0/image/size").asInt());

      // update with null should delete
      data.put("image", null);
      grapql.execute(
          new ExecutionInput.Builder()
              .query("mutation update($value:[TestFileInput]){update(TestFile:$value){message}}")
              .variables(Map.of("value", data))
              .build());
      assertEquals(
          0, execute("{TestFile{image{size,extension,url}}}").at("/TestFile/0/image/size").asInt());

      // reset
    } finally {
      grapql = new GraphqlApiFactory().createGraphqlForSchema(schema, taskService);
    }
  }

  @Test
  public void testTasksApi() throws IOException {
    // fake something into taskservice
    Task task = new Task("test");
    task.addSubTask(new Task("subtest"));
    taskService.submit(task);

    // list all tasks
    assertTrue(
        execute("{_tasks{id,description,status}}")
            .at("/_tasks/0/description")
            .textValue()
            .startsWith("test"));
    // load single task
    assertTrue(
        execute(
                "{_tasks(id:\""
                    + task.getId()
                    + "\"){id,description,status,subTasks{id,description,status,subTasks{id,description,status}}}}")
            .at("/_tasks/0/description")
            .textValue()
            .startsWith("test"));
  }

  @Test
  public void testTruncate() throws IOException {
    List<Row> result = schema.getTable("Order").retrieveRows();
    String message =
        execute("mutation {truncate(tables: \"Order\"){message}}").at("/truncate/message").asText();
    assertTrue(message.contains("Truncated"));
    List<Row> result2 = schema.getTable("Order").retrieveRows();
    assertTrue(result.size() > 0 && result2.size() == 0);

    // restore
    schema = database.dropCreateSchema(schemaName);
    new PetStoreLoader().load(schema, true);
  }

  @Test
  public void testReport() throws IOException {
    schema = database.dropCreateSchema(schemaName);
    new PetStoreLoader().load(schema, true);
    grapql = new GraphqlApiFactory().createGraphqlForSchema(schema, taskService);
    JsonNode result = execute("{_reports(id:0){data,count}}");
    assertTrue(result.at("/_reports/data").textValue().contains("pooky"));
    assertEquals(8, result.at("/_reports/count").intValue());
  }
}
