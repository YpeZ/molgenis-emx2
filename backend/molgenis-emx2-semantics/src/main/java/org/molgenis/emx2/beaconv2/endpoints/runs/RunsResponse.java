package org.molgenis.emx2.beaconv2.endpoints.runs;

import static org.molgenis.emx2.beaconv2.common.QueryHelper.mapToOntologyTerm;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import graphql.ExecutionResult;
import graphql.GraphQL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.molgenis.emx2.Table;
import org.molgenis.emx2.graphql.GraphqlApiFactory;
import org.molgenis.emx2.utils.TypeUtils;
import spark.Request;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RunsResponse {

  // annotation to print empty array as "[ ]" as required per Beacon spec
  @JsonInclude(JsonInclude.Include.ALWAYS)
  private RunsResultSets[] resultSets;

  // query parameters, ignore from output
  @JsonIgnore private String idForQuery;

  public RunsResponse(Request request, List<Table> tables) throws Exception {

    List<RunsResultSets> resultSetsList = new ArrayList<>();
    idForQuery = request.queryParams("id");

    for (Table table : tables) {
      List<RunsResultSetsItem> runsItemList = new ArrayList<>();

      GraphQL grapql = new GraphqlApiFactory().createGraphqlForSchema(table.getSchema());
      ExecutionResult executionResult =
          grapql.execute(
              "{Runs"
                  + (idForQuery != null ? "(filter:{id: {equals:\"" + idForQuery + "\"}})" : "")
                  + "{"
                  + "id,"
                  + "biosampleId,"
                  + "individualId,"
                  + "runDate,"
                  + "librarySource{name,codesystem,code},"
                  + "librarySelection,"
                  + "libraryStrategy,"
                  + "libraryLayout,"
                  + "platform,"
                  + "platformModel{name,codesystem,code}"
                  + "}}");

      Map<String, Object> result = executionResult.toSpecification();
      List<Map<String, Object>> runsListFromJSON =
          (List<Map<String, Object>>) ((HashMap<String, Object>) result.get("data")).get("Runs");

      if (runsListFromJSON != null) {
        for (Map map : runsListFromJSON) {
          RunsResultSetsItem runsItem = new RunsResultSetsItem();
          runsItem.setId(TypeUtils.toString(map.get("id")));
          runsItem.setBiosampleId(TypeUtils.toString(map.get("biosampleId")));
          runsItem.setIndividualId(TypeUtils.toString(map.get("individualId")));
          runsItem.setRunDate(TypeUtils.toString(map.get("runDate")));
          runsItem.setLibrarySource(mapToOntologyTerm((Map) map.get("librarySource")));
          runsItem.setLibrarySelection(TypeUtils.toString(map.get("librarySelection")));
          runsItem.setLibraryStrategy(TypeUtils.toString(map.get("libraryStrategy")));
          runsItem.setLibraryLayout(TypeUtils.toString(map.get("libraryLayout")));
          runsItem.setPlatform(TypeUtils.toString(map.get("platform")));
          runsItem.setPlatformModel(mapToOntologyTerm((Map) map.get("platformModel")));
          runsItemList.add(runsItem);
        }
      }

      if (runsItemList.size() > 0) {
        RunsResultSets runsResultSets =
            new RunsResultSets(
                table.getSchema().getName(),
                runsItemList.size(),
                runsItemList.toArray(new RunsResultSetsItem[runsItemList.size()]));
        resultSetsList.add(runsResultSets);
      }
    }
    this.resultSets = resultSetsList.toArray(new RunsResultSets[resultSetsList.size()]);
  }
}
