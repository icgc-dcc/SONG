/*
 * Copyright (c) 2018. Ontario Institute for Cancer Research
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package bio.overture.song.sdk.register;

import bio.overture.song.core.model.Analysis;
import bio.overture.song.core.model.AnalysisType;
import bio.overture.song.core.model.ExportedPayload;
import bio.overture.song.core.model.File;
import bio.overture.song.core.model.FileData;
import bio.overture.song.core.model.FileUpdateResponse;
import bio.overture.song.core.model.SubmitResponse;
import bio.overture.song.sdk.model.ListAnalysisTypesRequest;
import bio.overture.song.sdk.rest.RestClient;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static java.lang.Boolean.parseBoolean;

@RequiredArgsConstructor
public class Registry {

  @NonNull private final RestClient restClient;
  @NonNull private final Endpoint endpoint;

  public Registry(RestClient restClient) {
    this(restClient, new Endpoint());
  }

  /** Submit an payload to the song server. */
  public ResponseEntity<SubmitResponse> submit(@NonNull String studyId, @NonNull String json) {
    checkServerAlive();
    val url = endpoint.submit(studyId);
    return restClient.post(url, json, SubmitResponse.class);
  }

  public ResponseEntity<List<File>> getAnalysisFiles(@NonNull String studyId, @NonNull String analysisId) {
    checkServerAlive();
    val url = endpoint.getAnalysisFiles(studyId, analysisId);
    return restClient.getList(url, File.class);
  }

  public ResponseEntity<Analysis> getAnalysis(@NonNull String studyId, @NonNull String analysisId) {
    checkServerAlive();
    val url = endpoint.getAnalysis(studyId, analysisId);
    return restClient.get(url, Analysis.class);
  }

  public ResponseEntity<List<AnalysisType>> listAnalysisTypes(@NonNull ListAnalysisTypesRequest listAnalysisTypesRequest) {
    checkServerAlive();
    val url = endpoint.listAnalysisTypes(listAnalysisTypesRequest);
    return restClient.getList(url, AnalysisType.class);
  }

  public ResponseEntity<AnalysisType> registerAnalysisType(@NonNull String json) {
    checkServerAlive();
    val url = endpoint.registerAnalysisType();
    return restClient.post(url, json, AnalysisType.class);
  }

  /**
   * Returns true if the SONG server is running, otherwise false.
   *
   * @return boolean
   */
  public boolean isAlive() {
    val url = endpoint.isAlive();
    return parseBoolean(restClient.get(url, String.class).getBody());
  }

  /**
   * TODO: [DCC-5641] the ResponseEntity from AnalysisController is not returned, since
   * RestTemplate.put is a void method. need to find RestTemplate implementation that returns a
   * response
   */
  public ResponseEntity<String> publish(@NonNull String studyId, @NonNull String analysisId, boolean ignoreUndefinedMd5) {
    checkServerAlive();
    val url = endpoint.publish(studyId, analysisId, ignoreUndefinedMd5);
    return restClient.put(url, String.class);
  }

  public ResponseEntity<String> unpublish(@NonNull String studyId, @NonNull String analysisId) {
    checkServerAlive();
    val url = endpoint.unpublish(studyId, analysisId);
    return restClient.put(url, String.class);
  }

  public ResponseEntity<List<ExportedPayload>> exportStudy(@NonNull String studyId, boolean includeAnalysisId) {
    checkServerAlive();
    val url = endpoint.exportStudy(studyId, includeAnalysisId);
    return restClient.getList(url, ExportedPayload.class);
  }

  public ResponseEntity<List<ExportedPayload>> exportAnalyses(@NonNull List<String> analysisIds, boolean includeAnalysisId) {
    checkServerAlive();
    val url = endpoint.exportAnalysisIds(analysisIds, includeAnalysisId);
    return restClient.getList(url, ExportedPayload.class);
  }

  public ResponseEntity<String> suppress(@NonNull String studyId, @NonNull String analysisId) {
    checkServerAlive();
    val url = endpoint.suppress(studyId, analysisId);
    return restClient.put(url, String.class);
  }

  public ResponseEntity updateAnalysis(@NonNull String studyId, @NonNull String analysisId, @NonNull String updateAnalysisRequest) {
    checkServerAlive();
    val url = endpoint.updateAnalysis(studyId, analysisId);
    return restClient.put(url, updateAnalysisRequest, Object.class);
  }

  public ResponseEntity<FileUpdateResponse> updateFile(@NonNull String studyId, @NonNull String objectId, @NonNull FileData fileUpdateRequest) {
    checkServerAlive();
    val url = endpoint.updateFile(studyId, objectId);
    return restClient.put(url, fileUpdateRequest, FileUpdateResponse.class);
  }

  public ResponseEntity<List<Analysis>> idSearch(
      String studyId, String sampleId, String specimenId, String donorId, String fileId) {
    checkServerAlive();
    val url = endpoint.idSearch(studyId, sampleId, specimenId, donorId, fileId);
    return restClient.getList(url, Analysis.class);
  }

  public ResponseEntity<AnalysisType> getAnalysisType(@NonNull String name, Integer version, Boolean unrenderedOnly) {
    checkServerAlive();
    val url = endpoint.getAnalysisType(name, version, unrenderedOnly);
    return restClient.get(url, AnalysisType.class);
  }

  public void checkServerAlive() {
    if (!isAlive()) {
      throw new RestClientException("The song server is not reachable");
    }
  }
}
