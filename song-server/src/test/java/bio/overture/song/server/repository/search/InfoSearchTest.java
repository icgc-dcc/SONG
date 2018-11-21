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
package bio.overture.song.server.repository.search;

import bio.overture.song.core.utils.JsonUtils;
import bio.overture.song.core.utils.RandomGenerator;
import bio.overture.song.server.model.analysis.AbstractAnalysis;
import bio.overture.song.server.model.entity.Donor;
import bio.overture.song.server.model.entity.FileEntity;
import bio.overture.song.server.model.entity.Info;
import bio.overture.song.server.model.entity.Sample;
import bio.overture.song.server.model.entity.Specimen;
import bio.overture.song.server.repository.InfoRepository;
import bio.overture.song.server.service.AnalysisService;
import bio.overture.song.server.service.StudyService;
import bio.overture.song.server.service.UploadService;
import bio.overture.song.server.utils.TestFiles;
import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.icgc.dcc.common.core.json.JsonNodeBuilders;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.icgc.dcc.common.core.util.Joiners.PATH;
import static bio.overture.song.core.utils.JsonUtils.toJson;
import static bio.overture.song.core.utils.RandomGenerator.createRandomGenerator;
import static bio.overture.song.server.model.entity.InfoPK.createInfoPK;
import static bio.overture.song.server.repository.search.InfoSearchRequest.createInfoSearchRequest;
import static bio.overture.song.server.repository.search.InfoSearchResponse.createWithInfo;
import static bio.overture.song.server.repository.search.InfoSearchResponse.createWithoutInfo;
import static bio.overture.song.server.repository.search.SearchTerm.parseSearchTerms;
import static bio.overture.song.server.utils.generator.PayloadGenerator.updateStudyInPayload;
import static bio.overture.song.server.utils.TestConstants.DEFAULT_ANALYSIS_ID;
import static bio.overture.song.server.utils.TestFiles.getJsonNodeFromClasspath;
import static bio.overture.song.server.utils.generator.StudyGenerator.createStudyGenerator;

@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
@ActiveProfiles("dev")
public class InfoSearchTest {

  private static final Set<String> TEST_PAYLOAD_FILENAMES_1 = newHashSet(
      "testData_0.json",
      "testData_1.json",
      "testData_2.json",
      "testData_3.json",
      "testData_4.json",
      "testData_5.json",
      "testData_6.json",
      "testData_7.json",
      "testData_8.json"
  );

  @Autowired private AnalysisService service;
  @Autowired private UploadService uploadService;
  @Autowired private InfoRepository infoRepository;
  @Autowired private StudyService studyService;

  private Map<String, InfoSearchResponse> analysisRespMap;
  private RandomGenerator randomGenerator = createRandomGenerator(InfoSearchTest.class.getSimpleName());
  private String studyId1;
  private String studyId2;

  @Before
  public void init(){

    if (analysisRespMap == null){//load it only once
      val studyGenerator = createStudyGenerator(studyService,randomGenerator);
      studyId1 = studyGenerator.createRandomStudy();
      studyId2 = studyGenerator.createRandomStudy();

      this.analysisRespMap = Maps.<String, InfoSearchResponse>newHashMap();
      for (val payloadPath : TEST_PAYLOAD_FILENAMES_1){
        val expectedResponse1 = extractResponse(studyId1, payloadPath);
        analysisRespMap.put(expectedResponse1.getAnalysisId(), expectedResponse1);
        val expectedResponse2 = extractResponse(studyId2, payloadPath);
        analysisRespMap.put(expectedResponse2.getAnalysisId(), expectedResponse2);
      }
    } else {
      studyService.checkStudyExist(studyId1);
      studyService.checkStudyExist(studyId2);
    }
  }

  @Test
  public void testInfoSearchResponse(){
    val info = JsonNodeBuilders.object()
        .with("key1", "value1")
        .with("key2", "value2")
        .end();

    val withInfoResponse = createWithInfo(DEFAULT_ANALYSIS_ID, info);
    assertThat(withInfoResponse.getInfo()).isEqualTo(info);
    assertThat(withInfoResponse.hasInfo()).isTrue();

    val withoutInfoResponse = createWithoutInfo(DEFAULT_ANALYSIS_ID);
    assertThat(withoutInfoResponse.getInfo()).isNull();
    assertThat(withoutInfoResponse.hasInfo()).isFalse();
  }

  @Test
  public void testBasicTermSearch() {
    val term1 = "dataCategorization.dataType=SSM";
    val term2 = "dataCategorization.experimentalStrategy=WGS";
    runBasicTermSearchTest(() ->  search(studyId1, true, term1, term2), true);
    runBasicTermSearchTest(() ->  search(studyId1, false, term1, term2), false);
    runBasicTermSearchTest(() ->  search2(studyId1,false, term1, term2), false);
    runBasicTermSearchTest(() ->  search2(studyId1,true, term1, term2), true);
    runBasicTermSearchTest(() ->  search(studyId2, true, term1, term2), true);
    runBasicTermSearchTest(() ->  search(studyId2, false, term1, term2), false);
    runBasicTermSearchTest(() ->  search2(studyId2,false, term1, term2), false);
    runBasicTermSearchTest(() ->  search2(studyId2,true, term1, term2), true);

    val response1 = search(studyId1, true, term1, term2);
    val response2 = search(studyId2, true, term1, term2);
    Assertions.assertThat(response1).hasSize(1);
    Assertions.assertThat(response2).hasSize(1);
    val result1 = response1.get(0);
    val result2 = response2.get(0);
    assertThat(result1.getAnalysisId()).isNotEqualTo(result2.getAnalysisId());
    assertThat(result1.getInfo()).isEqualTo(result2.getInfo());
    val an1 = service.securedDeepRead(studyId1, result1.getAnalysisId());
    val an2 = service.securedDeepRead(studyId2, result2.getAnalysisId());
    assertAnalysisData(an1, an2);

    assert(true);
  }

  private static void assertAnalysisData(AbstractAnalysis a1, AbstractAnalysis a2){
    assertThat(a1.getAnalysisType()).isEqualTo(a2.getAnalysisType());
    assertThat(a1.getAnalysisState()).isEqualTo(a2.getAnalysisState());
    assertThat(a1.getFile().size()).isEqualTo(a2.getFile().size());
    assertThat(a1.getInfoAsString()).isEqualTo(a2.getInfoAsString());
    val f2Map = a2.getFile().stream().collect(groupingBy(FileEntity::getFileName));
    for (val f1 : a1.getFile()){
      assertThat(f2Map).containsKey(f1.getFileName());
      val f2result = f2Map.get(f1.getFileName());
      assertThat(f2result).hasSize(1);
      val f2 = f2result.get(0);
      assertFileData(f1, f2);
    }
    assertThat(a1.getSample().size()).isEqualTo(a2.getSample().size());
    val s2map = a2.getSample().stream().collect(groupingBy(Sample::getSampleSubmitterId));
    for (val s1 : a1.getSample()){
      assertThat(s2map).containsKey(s1.getSampleSubmitterId());
      val s2result = s2map.get(s1.getSampleSubmitterId());
      assertThat(s2result).hasSize(1);
      val s2 = s2result.get(0);
      assertSampleData(s1, s2);
      assertDonorData(s1.getDonor(), s2.getDonor());
      assertSpecimenData(s1.getSpecimen(), s2.getSpecimen());
    }
  }

  private static void assertDonorData(Donor d1, Donor d2){
    assertThat(d1.getDonorGender()).isEqualTo(d2.getDonorGender());
    assertThat(d1.getDonorSubmitterId()).isEqualTo(d2.getDonorSubmitterId());
    assertThat(d1.getInfoAsString()).isEqualTo(d2.getInfoAsString());
  }

  private static void assertSpecimenData(Specimen s1, Specimen s2){
    assertThat(s1.getSpecimenClass()).isEqualTo(s2.getSpecimenClass());
    assertThat(s1.getSpecimenSubmitterId()).isEqualTo(s2.getSpecimenSubmitterId());
    assertThat(s1.getSpecimenType()).isEqualTo(s2.getSpecimenType());
    assertThat(s1.getInfoAsString()).isEqualTo(s2.getInfoAsString());
  }

  private static void assertSampleData(Sample s1, Sample s2){
    assertThat(s1.getSampleSubmitterId()).isEqualTo(s2.getSampleSubmitterId());
    assertThat(s1.getSampleType()).isEqualTo(s2.getSampleType());
    assertThat(s1.getInfoAsString()).isEqualTo(s2.getInfoAsString());
  }
  private static void assertFileData(FileEntity f1, FileEntity f2){
    assertThat(f1.getFileAccess()).isEqualTo(f2.getFileAccess());
    assertThat(f1.getFileMd5sum()).isEqualTo(f2.getFileMd5sum());
    assertThat(f1.getFileName()).isEqualTo(f2.getFileName());
    assertThat(f1.getFileSize()).isEqualTo(f2.getFileSize());
    assertThat(f1.getFileType()).isEqualTo(f2.getFileType());
    assertThat(f1.getInfoAsString()).isEqualTo(f2.getInfoAsString());
  }

  @SneakyThrows
  private void runBasicTermSearchTest(Supplier<List<InfoSearchResponse>> responseSupplier, boolean shouldHaveInfo){
    val actualResponseList1 = responseSupplier.get();
    Assertions.assertThat(actualResponseList1).hasSize(1);
    val infoSearchResponse1 = actualResponseList1.get(0);
    Assertions.assertThat(this.analysisRespMap).containsKey(infoSearchResponse1.getAnalysisId());
    if (shouldHaveInfo){
      assertThat(infoSearchResponse1).isEqualTo(this.analysisRespMap.get(infoSearchResponse1.getAnalysisId()));
    }
    assertThat(infoSearchResponse1.hasInfo()).isEqualTo(shouldHaveInfo);
  }

  private InfoSearchResponse loadAndCreateResponse1(String study, String payloadString) throws Exception {
    val uploadStatus = uploadService.upload(study, payloadString, false );
    log.info(format("Got uploadStatus='%s'",uploadStatus));
    val uploadId = fromStatus(uploadStatus,"uploadId");
    val upload = uploadService.securedRead(study, uploadId);
    assertThat(upload.getState()).isEqualTo("VALIDATED");
    val resp = uploadService.save(study, uploadId, false);
    val analysisId = fromStatus(resp,"analysisId");

    val infoPK = createInfoPK(analysisId, "Analysis");
    val infoResult = infoRepository.findById(infoPK);
    val info = infoResult.map(Info::getInfo)
        .map(JsonUtils::toJsonNode)
        .map(JsonUtils::toJson)
        .orElse(null);
    return createWithInfo(analysisId, JsonUtils.readTree(info) );
  }

  @SneakyThrows
  private List<InfoSearchResponse> search(String studyId, boolean includeInfo, String ... searchTermStrings){
    val searchTerms = parseSearchTerms(searchTermStrings);
    val req = createInfoSearchRequest(includeInfo, searchTerms);
    return service.infoSearch(studyId, req);
  }

  @SneakyThrows
  private List<InfoSearchResponse> search2(String studyId, boolean includeInfo, String ... searchTermStrings){
    val searchTerms = parseSearchTerms(searchTermStrings);
    val map = new LinkedMultiValueMap<String, String>();
    searchTerms.forEach(x-> map.put(x.getKey(), newArrayList(x.getValue())));
    return service.infoSearch(studyId, includeInfo, map);
  }

  @SneakyThrows
  private InfoSearchResponse extractResponse(String study, String payloadPath){
    val testDataPath = PATH.join(TestFiles.SEARCH_TEST_DIR, payloadPath);
    val testDataNode = getJsonNodeFromClasspath(testDataPath);
    updateStudyInPayload(testDataNode, study);
    val testDataString = toJson(testDataNode);
    return loadAndCreateResponse1(study, testDataString);
  }

  @SneakyThrows
  private String fromStatus( ResponseEntity<String> uploadStatus, String key) {
    val uploadId = JsonUtils.readTree(uploadStatus.getBody()).at("/"+key).asText("");
    return uploadId;
  }

}
