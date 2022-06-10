package org.molgenis.emx2.beaconv2.responses;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.List;
import org.molgenis.emx2.Table;
import org.molgenis.emx2.beaconv2.responses.biosamples.BiosamplesResponse;
import org.molgenis.emx2.beaconv2.responses.datasets.DatasetsMeta;
import org.molgenis.emx2.beaconv2.responses.datasets.ResponseSummary;
import spark.Request;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Biosamples {

  DatasetsMeta meta;
  ResponseSummary responseSummary;
  BiosamplesResponse response;

  public Biosamples(Request request, List<Table> tables) throws Exception {
    this.meta = new DatasetsMeta("../beaconDatasetResponse.json", "datasets");
    this.response = new BiosamplesResponse(request, tables);
    this.responseSummary = new ResponseSummary();
  }
}
