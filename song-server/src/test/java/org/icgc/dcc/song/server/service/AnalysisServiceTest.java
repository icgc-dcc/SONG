/*
 * Copyright (c) 2017 The Ontario Institute for Cancer Research. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.icgc.dcc.song.server.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.dcc.song.core.utils.JsonUtils;
import org.icgc.dcc.song.core.utils.RandomGenerator;
import org.icgc.dcc.song.server.model.Metadata;
import org.icgc.dcc.song.server.model.analysis.Analysis;
import org.icgc.dcc.song.server.model.analysis.SequencingReadAnalysis;
import org.icgc.dcc.song.server.model.analysis.VariantCallAnalysis;
import org.icgc.dcc.song.server.model.entity.File;
import org.icgc.dcc.song.server.model.entity.Sample;
import org.icgc.dcc.song.server.model.entity.Study;
import org.icgc.dcc.song.server.model.entity.composites.CompositeEntity;
import org.icgc.dcc.song.server.repository.AnalysisRepository;
import org.icgc.dcc.song.server.repository.SampleRepository;
import org.icgc.dcc.song.server.utils.AnalysisGenerator;
import org.icgc.dcc.song.server.utils.PayloadGenerator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.icgc.dcc.song.core.exceptions.ServerErrors.ANALYSIS_ID_NOT_FOUND;
import static org.icgc.dcc.song.core.exceptions.ServerErrors.ANALYSIS_MISSING_FILES;
import static org.icgc.dcc.song.core.exceptions.ServerErrors.ANALYSIS_MISSING_SAMPLES;
import static org.icgc.dcc.song.core.exceptions.ServerErrors.DUPLICATE_ANALYSIS_ATTEMPT;
import static org.icgc.dcc.song.core.exceptions.ServerErrors.SEQUENCING_READ_NOT_FOUND;
import static org.icgc.dcc.song.core.exceptions.ServerErrors.STUDY_ID_DOES_NOT_EXIST;
import static org.icgc.dcc.song.core.exceptions.ServerErrors.UNPUBLISHED_FILE_IDS;
import static org.icgc.dcc.song.core.exceptions.ServerErrors.VARIANT_CALL_NOT_FOUND;
import static org.icgc.dcc.song.core.testing.SongErrorAssertions.assertSongError;
import static org.icgc.dcc.song.core.utils.JsonUtils.fromJson;
import static org.icgc.dcc.song.core.utils.JsonUtils.toJson;
import static org.icgc.dcc.song.core.utils.RandomGenerator.createRandomGenerator;
import static org.icgc.dcc.song.server.model.enums.AnalysisStates.UNPUBLISHED;
import static org.icgc.dcc.song.server.model.enums.AnalysisTypes.SEQUENCING_READ;
import static org.icgc.dcc.song.server.model.enums.AnalysisTypes.VARIANT_CALL;
import static org.icgc.dcc.song.server.model.enums.AnalysisTypes.resolveAnalysisType;
import static org.icgc.dcc.song.server.repository.search.IdSearchRequest.createIdSearchRequest;
import static org.icgc.dcc.song.server.service.ExistenceService.createExistenceService;
import static org.icgc.dcc.song.server.utils.AnalysisGenerator.createAnalysisGenerator;
import static org.icgc.dcc.song.server.utils.PayloadGenerator.createPayloadGenerator;
import static org.icgc.dcc.song.server.utils.StudyGenerator.createStudyGenerator;
import static org.icgc.dcc.song.server.utils.TestFiles.assertInfoKVPair;
import static org.icgc.dcc.song.server.utils.TestFiles.getJsonStringFromClasspath;
import static org.springframework.http.HttpStatus.OK;

@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class})
@ActiveProfiles({"dev", "test"})
public class AnalysisServiceTest {

  private static final String DEFAULT_STUDY_ID = "ABC123";

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

  @Autowired
  FileService fileService;
  @Autowired
  AnalysisService service;
  @Autowired
  IdService idService;
  @Autowired
  private StudyService studyService;
  @Autowired
  private SampleRepository sampleRepository;
  @Autowired
  private AnalysisRepository analysisRepository;

  private final RandomGenerator randomGenerator = createRandomGenerator(AnalysisServiceTest.class.getSimpleName());

  private PayloadGenerator payloadGenerator;
  private AnalysisGenerator analysisGenerator;

  @Autowired
  private RetryTemplate retryTemplate;

  @Before
  public void beforeTest(){
    assertThat(studyService.isStudyExist(DEFAULT_STUDY_ID)).isTrue();
  }

  /**
   * This is dirty, but since the existenceService is so easy to construct
   * and the storage url port is randomly assigned, it's worth it.
   */
  @Before
  public void init(){
    this.payloadGenerator = createPayloadGenerator(randomGenerator);
    this.analysisGenerator = createAnalysisGenerator(DEFAULT_STUDY_ID, service, payloadGenerator);
    val testStorageUrl = format("http://localhost:%s", wireMockRule.port());
    val testExistenceService = createExistenceService(retryTemplate,testStorageUrl);
    ReflectionTestUtils.setField(service, "existence", testExistenceService);
    log.info("ExistenceService configured to endpoint: {}",testStorageUrl );
  }

  @Test
  public void testCreateAndUpdate() {
    val created = analysisGenerator.createDefaultRandomSequencingReadAnalysis();
    val analysisId = created.getAnalysisId();
    assertThat(created.getAnalysisId()).isEqualTo(analysisId);
    assertThat(created.getAnalysisState()).isEqualTo("UNPUBLISHED");
    assertThat(created.getAnalysisType()).isEqualTo("sequencingRead");
    assertThat(created.getSample().size()).isEqualTo(1);
    val sample = created.getSample().get(0);
    val experiment = ((SequencingReadAnalysis) created).getExperiment();
    assertThat(experiment).isNotNull();
    assertThat(experiment.getAlignmentTool().equals("BigWrench"));
    val expectedMetadata = new Metadata();
    expectedMetadata.setInfo("marginOfError", "0.01%");
    assertThat(experiment.getInfo()).isEqualTo(expectedMetadata.getInfo());

;    // test update
    val change="ModifiedToolName";
    experiment.setAlignmentTool(change);
    service.updateAnalysis(DEFAULT_STUDY_ID, created);
    val gotBack = service.read(analysisId);
    val experiment2 =((SequencingReadAnalysis)gotBack).getExperiment();
    assertThat(experiment2.getAlignmentTool() ).isEqualTo(change);

    log.info(format("Created '%s'",toJson(created)));
  }

  @Test
  public void testIsAnalysisExist(){
    val analysis = analysisGenerator.createDefaultRandomVariantCallAnalysis();
    val randomAnalysisId = randomGenerator.generateRandomUUID().toString();
    analysis.setAnalysisId(randomAnalysisId);
    assertThat(service.doesAnalysisIdExist(randomAnalysisId)).isFalse();
    val actualAnalysisId = service.create(DEFAULT_STUDY_ID, analysis, false);
    assertThat(actualAnalysisId).isEqualTo(randomAnalysisId);
    assertThat(service.doesAnalysisIdExist(randomAnalysisId)).isTrue();
  }

  @Test
  public void testCreateAndUpdateVariantCall() {
    val created = analysisGenerator.createRandomAnalysis(VariantCallAnalysis.class,
        "documents/variantcall-valid-1.json");
    val analysisId = created.getAnalysisId();
    assertThat(created.getAnalysisId()).isEqualTo(analysisId);
    assertThat(created.getAnalysisState()).isEqualTo(UNPUBLISHED.toString());
    assertThat(created.getAnalysisType()).isEqualTo("variantCall");
    assertThat(created.getSample().size()).isEqualTo(1);
    val sample = created.getSample().get(0);
    val experiment = ((VariantCallAnalysis) created).getExperiment();
    assertThat(experiment).isNotNull();
    assertThat(experiment.getVariantCallingTool()).isEqualTo("silver bullet");
    assertThat(experiment.getInfoAsString()).isEqualTo(
            JsonUtils.fromSingleQuoted("{\"extraInfo\":\"this is extra info\"}"));
    // test update
    val change="GoldenHammer";
    experiment.setVariantCallingTool(change) ;
    service.updateAnalysis(DEFAULT_STUDY_ID, created);
    val gotBack = service.read(analysisId);
    val experiment2 =((VariantCallAnalysis)gotBack).getExperiment();
    assertThat(experiment2.getVariantCallingTool()).isEqualTo(change);

    log.info(format("Created '%s'",toJson(created)));
  }

  @Test
  public void testReadAnalysisDNE() {
    val nonExistentAnalysisId = randomGenerator.generateRandomUUID().toString();
    assertThat(service.doesAnalysisIdExist(nonExistentAnalysisId)).isFalse();
    assertSongError(() -> service.read(nonExistentAnalysisId), ANALYSIS_ID_NOT_FOUND);
  }

  @Test
  public void testReadVariantCallDNE() {
    val analysis = analysisGenerator.createDefaultRandomVariantCallAnalysis();
    val analysisId = analysis.getAnalysisId();

    analysisRepository.deleteVariantCall(analysisId);
    assertThat(analysisRepository.readVariantCall(analysisId)).isNull();
    assertSongError(() -> service.read(analysisId), VARIANT_CALL_NOT_FOUND);
  }

  @Test
  public void testReadSequencingReadDNE() {

    val analysis = analysisGenerator.createDefaultRandomSequencingReadAnalysis();
    val analysisId = analysis.getAnalysisId();

    analysisRepository.deleteSequencingRead(analysisId);
    assertThat(analysisRepository.readSequencingRead(analysisId)).isNull();
    assertSongError(() -> service.read(analysisId), SEQUENCING_READ_NOT_FOUND);
  }

  @Test
  public void testReadVariantCall(){
    val json = getJsonStringFromClasspath("documents/variantcall-read-test.json");
    val analysisRaw = fromJson(json, VariantCallAnalysis.class);
    val analysisId = service.create(DEFAULT_STUDY_ID, analysisRaw, false);
    val a = (VariantCallAnalysis)service.read(analysisId);

    //Asserting Analysis
    assertThat(a.getAnalysisState()).isEqualTo("UNPUBLISHED");
    assertThat(a.getAnalysisType()).isEqualTo("variantCall");
    assertThat(a.getStudy()).isEqualTo(DEFAULT_STUDY_ID);
    assertInfoKVPair(a, "description1","description1 for this variantCall analysis an01" );
    assertInfoKVPair(a, "description2","description2 for this variantCall analysis an01" );

    val experiment = a.getExperiment();
    assertThat(experiment.getAnalysisId()).isEqualTo(analysisId);
    assertThat(experiment.getVariantCallingTool()).isEqualTo("silver bullet ex01");
    assertThat(experiment.getMatchedNormalSampleSubmitterId()).isEqualTo("sample x24-11a");
    assertInfoKVPair(experiment, "extraExperimentInfo","some more data for a variantCall experiment ex01");

    //Asserting Sample
    assertThat(a.getSample()).hasSize(2);
    val sample0 = a.getSample().get(0);
    assertThat(sample0.getSampleSubmitterId()).isEqualTo("internal_sample_98024759826836_fs01");
    assertThat(sample0.getSampleType()).isEqualTo("Total RNA");
    assertInfoKVPair(sample0, "extraSampleInfo","some more data for a variantCall sample_fs01");

    val donor00 = sample0.getDonor();
    assertThat(donor00.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);
    assertThat(donor00.getDonorGender()).isEqualTo("male");
    assertThat(donor00.getDonorSubmitterId()).isEqualTo("internal_donor_123456789-00_fs01");
    assertInfoKVPair(donor00, "extraDonorInfo", "some more data for a variantCall donor_fs01");

    val specimen00 = sample0.getSpecimen();
    assertThat(specimen00.getDonorId()).isEqualTo(donor00.getDonorId());
    assertThat(specimen00.getSpecimenClass()).isEqualTo("Tumour");
    assertThat(specimen00.getSpecimenType()).isEqualTo("Primary tumour - other");
    assertThat(sample0.getSpecimenId()).isEqualTo(specimen00.getSpecimenId());
    assertInfoKVPair(specimen00, "extraSpecimenInfo_0", "first for a variantCall specimen_fs01");
    assertInfoKVPair(specimen00, "extraSpecimenInfo_1", "second data for a variantCall specimen_fs01");

    val sample1 = a.getSample().get(1);
    assertThat(sample1.getSampleSubmitterId()).isEqualTo("internal_sample_98024759826836_fs02");
    assertThat(sample1.getSampleType()).isEqualTo("Total RNA");
    assertInfoKVPair(sample1, "extraSampleInfo","some more data for a variantCall sample_fs02");

    val donor01 = sample1.getDonor();
    assertThat(donor01.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);
    assertThat(donor01.getDonorGender()).isEqualTo("female");
    assertThat(donor01.getDonorSubmitterId()).isEqualTo("internal_donor_123456789-00_fs02");
    assertInfoKVPair(donor01, "extraDonorInfo_0", "first data for a variantCall donor_fs02");
    assertInfoKVPair(donor01, "extraDonorInfo_1","second data for a variantCall donor_fs02");

    val specimen01 = sample1.getSpecimen();
    assertThat(specimen01.getDonorId()).isEqualTo(donor01.getDonorId());
    assertThat(specimen01.getSpecimenClass()).isEqualTo("Tumour");
    assertThat(specimen01.getSpecimenType()).isEqualTo("Primary tumour - other");
    assertThat(sample1.getSpecimenId()).isEqualTo(specimen01.getSpecimenId());
    assertInfoKVPair(specimen01, "extraSpecimenInfo", "some more data for a variantCall specimen_fs02");

    assertThat(a.getFile()).hasSize(3);
    val file0 = a.getFile().get(0);
    val file1 = a.getFile().get(1);
    val file2 = a.getFile().get(2);
    assertThat(file0.getAnalysisId()).isEqualTo(analysisId);
    assertThat(file1.getAnalysisId()).isEqualTo(analysisId);
    assertThat(file2.getAnalysisId()).isEqualTo(analysisId);
    assertThat(file0.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);
    assertThat(file1.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);
    assertThat(file2.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);

    val fileName0 = "a3bc0998a-3521-43fd-fa10-a834f3874e46-fn1.MUSE_1-0rc-vcf.20170711.somatic.snv_mnv.vcf.gz";
    val fileName1 ="a3bc0998a-3521-43fd-fa10-a834f3874e46-fn2.MUSE_1-0rc-vcf.20170711.somatic.snv_mnv.vcf.gz";
    val fileName2 = "a3bc0998a-3521-43fd-fa10-a834f3874e46-fn3.MUSE_1-0rc-vcf.20170711.somatic.snv_mnv.vcf.gz.idx";

    for (val file : a.getFile()){
      if (file.getFileName().equals(fileName0)){
        assertThat(file.getFileName()).isEqualTo(fileName0);
        assertThat(file.getFileSize()).isEqualTo(376953);
        assertThat(file.getFileMd5sum()).isEqualTo("652b2e2b7133229a89650de27ad7fc41");
        assertThat(file.getFileAccess()).isEqualTo("controlled");
        assertThat(file.getFileType()).isEqualTo("VCF");
        assertInfoKVPair(file, "extraFileInfo_0", "first data for variantCall file_fn1");
        assertInfoKVPair(file, "extraFileInfo_1", "second data for variantCall file_fn1");
      } else if (file.getFileName().equals(fileName1)){
        assertThat(file.getFileName()).isEqualTo(fileName1);
        assertThat(file.getFileSize()).isEqualTo(983820);
        assertThat(file.getFileMd5sum()).isEqualTo("b8b743a499e461922accad58fdbf25d2");
        assertThat(file.getFileAccess()).isEqualTo("open");
        assertThat(file.getFileType()).isEqualTo("VCF");
        assertInfoKVPair(file, "extraFileInfo", "some more data for variantCall file_fn2");

      } else if (file.getFileName().equals(fileName2)){
        assertThat(file.getFileName()).isEqualTo(fileName2);
        assertThat(file.getFileSize()).isEqualTo(4840);
        assertThat(file.getFileMd5sum()).isEqualTo("2b80298c2f312df7db482105053f889b");
        assertThat(file.getFileAccess()).isEqualTo("open");
        assertThat(file.getFileType()).isEqualTo("IDX");
        assertInfoKVPair(file, "extraFileInfo", "some more data for variantCall file_fn3");
      } else {
        fail(format("the fileName %s is not recognized", file.getFileName()));
      }
    }
  }

  @Test
  public void testReadSequencingRead(){
    val json = getJsonStringFromClasspath("documents/sequencingread-read-test.json");
    val analysisRaw = fromJson(json, SequencingReadAnalysis.class);
    val analysisId = service.create(DEFAULT_STUDY_ID, analysisRaw, false);
    val a = (SequencingReadAnalysis)service.read(analysisId);

    //Asserting Analysis
    assertThat(a.getAnalysisState()).isEqualTo("UNPUBLISHED");
    assertThat(a.getAnalysisType()).isEqualTo("sequencingRead");
    assertThat(a.getStudy()).isEqualTo(DEFAULT_STUDY_ID);
    assertInfoKVPair(a, "description1","description1 for this sequencingRead analysis an01" );
    assertInfoKVPair(a, "description2","description2 for this sequencingRead analysis an01" );

    val experiment = a.getExperiment();
    assertThat(experiment.getAnalysisId()).isEqualTo(analysisId);
    assertThat(experiment.getLibraryStrategy()).isEqualTo("WXS");
    assertThat(experiment.getPairedEnd()).isFalse();
    assertThat(experiment.getInsertSize()).isEqualTo(92736);
    assertThat(experiment.getAligned()).isTrue();
    assertThat(experiment.getAlignmentTool()).isEqualTo("myCool Sequence ReadingTool");
    assertThat(experiment.getReferenceGenome()).isEqualTo("someSeq Genome");
    assertInfoKVPair(experiment, "extraExperimentInfo", "some more data for a sequencingRead experiment ex02");

    val sampleMap = Maps.<String, CompositeEntity>newHashMap();


    //Asserting Sample
    assertThat(a.getSample()).hasSize(2);
    val sample0 = a.getSample().get(0);
    sampleMap.put(sample0.getSampleId(), sample0);
    assertThat(sample0.getSampleSubmitterId()).isEqualTo("internal_sample_98024759826836_fr01");
    assertThat(sample0.getSampleType()).isEqualTo("Total RNA");
    assertInfoKVPair(sample0, "extraSampleInfo","some more data for a sequencingRead sample_fr01");

    val donor00 = sample0.getDonor();
    assertThat(donor00.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);
    assertThat(donor00.getDonorGender()).isEqualTo("male");
    assertThat(donor00.getDonorSubmitterId()).isEqualTo("internal_donor_123456789-00_fr01");
    assertInfoKVPair(donor00, "extraDonorInfo", "some more data for a sequencingRead donor_fr01");

    val specimen00 = sample0.getSpecimen();
    assertThat(specimen00.getDonorId()).isEqualTo(donor00.getDonorId());
    assertThat(specimen00.getSpecimenClass()).isEqualTo("Tumour");
    assertThat(specimen00.getSpecimenType()).isEqualTo("Primary tumour - other");
    assertThat(sample0.getSpecimenId()).isEqualTo(specimen00.getSpecimenId());
    assertInfoKVPair(specimen00, "extraSpecimenInfo_0", "first for a sequencingRead specimen_fr01");
    assertInfoKVPair(specimen00, "extraSpecimenInfo_1", "second data for a sequencingRead specimen_fr01");

    val sample1 = a.getSample().get(1);
    sampleMap.put(sample1.getSampleId(), sample1);
    assertThat(sample1.getSampleSubmitterId()).isEqualTo("internal_sample_98024759826836_fr02");
    assertThat(sample1.getSampleType()).isEqualTo("Total RNA");
    assertInfoKVPair(sample1, "extraSampleInfo","some more data for a sequencingRead sample_fr02");

    val donor01 = sample1.getDonor();
    assertThat(donor01.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);
    assertThat(donor01.getDonorGender()).isEqualTo("female");
    assertThat(donor01.getDonorSubmitterId()).isEqualTo("internal_donor_123456789-00_fr02");
    assertInfoKVPair(donor01, "extraDonorInfo_0", "first data for a sequencingRead donor_fr02");
    assertInfoKVPair(donor01, "extraDonorInfo_1","second data for a sequencingRead donor_fr02");

    val specimen01 = sample1.getSpecimen();
    assertThat(specimen01.getDonorId()).isEqualTo(donor01.getDonorId());
    assertThat(specimen01.getSpecimenClass()).isEqualTo("Tumour");
    assertThat(specimen01.getSpecimenType()).isEqualTo("Primary tumour - other");
    assertThat(sample1.getSpecimenId()).isEqualTo(specimen01.getSpecimenId());
    assertInfoKVPair(specimen01, "extraSpecimenInfo", "some more data for a sequencingRead specimen_fr02");

    assertThat(a.getFile()).hasSize(3);
    val file0 = a.getFile().get(0);
    val file1 = a.getFile().get(1);
    val file2 = a.getFile().get(2);
    assertThat(file0.getAnalysisId()).isEqualTo(analysisId);
    assertThat(file1.getAnalysisId()).isEqualTo(analysisId);
    assertThat(file2.getAnalysisId()).isEqualTo(analysisId);
    assertThat(file0.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);
    assertThat(file1.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);
    assertThat(file2.getStudyId()).isEqualTo(DEFAULT_STUDY_ID);

    val fileName0 = "a3bc0998a-3521-43fd-fa10-a834f3874e46-fn1.MUSE_1-0rc-vcf.20170711.bam";
    val fileName1 = "a3bc0998a-3521-43fd-fa10-a834f3874e46-fn2.MUSE_1-0rc-vcf.20170711.bam";
    val fileName2 = "a3bc0998a-3521-43fd-fa10-a834f3874e46-fn3.MUSE_1-0rc-vcf.20170711.bam.bai";
    val fileMap = Maps.<String, File>newHashMap();

    for (val file : a.getFile()){
      fileMap.put(file.getFileName(), file);
      if (file.getFileName().equals(fileName0)){
        assertThat(file.getFileName()).isEqualTo(fileName0);
        assertThat(file.getFileSize()).isEqualTo(1212121);
        assertThat(file.getFileMd5sum()).isEqualTo("e2324667df8085eddfe95742047e153f");
        assertThat(file.getFileAccess()).isEqualTo("controlled");
        assertThat(file.getFileType()).isEqualTo("BAM");
        assertInfoKVPair(file, "extraFileInfo_0", "first data for sequencingRead file_fn1");
        assertInfoKVPair(file, "extraFileInfo_1", "second data for sequencingRead file_fn1");
      } else if (file.getFileName().equals(fileName1)){
        assertThat(file.getFileName()).isEqualTo(fileName1);
        assertThat(file.getFileSize()).isEqualTo(34343);
        assertThat(file.getFileMd5sum()).isEqualTo("8b5379a29aac642d6fe1808826bd9e49");
        assertThat(file.getFileAccess()).isEqualTo("open");
        assertThat(file.getFileType()).isEqualTo("BAM");
        assertInfoKVPair(file, "extraFileInfo", "some more data for sequencingRead file_fn2");

      } else if (file.getFileName().equals(fileName2)){
        assertThat(file.getFileName()).isEqualTo(fileName2);
        assertThat(file.getFileSize()).isEqualTo(4840);
        assertThat(file.getFileMd5sum()).isEqualTo("61da923f32863a9c5fa3d2a0e19bdee3");
        assertThat(file.getFileAccess()).isEqualTo("open");
        assertThat(file.getFileType()).isEqualTo("BAI");
        assertInfoKVPair(file, "extraFileInfo", "some more data for sequencingRead file_fn3");
      } else {
        fail(format("the fileName %s is not recognized", file.getFileName()));
      }
    }

    // Test the readFiles method
    for (val file : service.readFiles(analysisId)){
      assertThat(fileMap).containsKeys(file.getFileName());
      assertThat(file).isEqualTo(fileMap.get(file.getFileName()));
    }

    // Test readSample method
    for (val compositeEntity: service.readSamples(analysisId)){
      assertThat(sampleMap).containsKeys(compositeEntity.getSampleId());
      assertThat(compositeEntity).isEqualTo(sampleMap.get(compositeEntity.getSampleId()));
    }

    assertThat(service.readSequencingRead(analysisId)).isEqualTo(experiment);
  }

  private void setUpDccStorageMockService(boolean expectedResult){
    wireMockRule.resetAll();
    wireMockRule.stubFor(get(urlMatching("/upload/.*"))
        .willReturn(aResponse()
            .withStatus(OK.value())
            .withBody(Boolean.toString(expectedResult))));
  }

  @Test
  public void testPublish() {
    setUpDccStorageMockService(true);
    val token = "mockToken";
    val id = "AN1";
    service.publish(token, id);

    val analysis = service.read(id);
    assertThat(analysis.getAnalysisState()).isEqualTo("PUBLISHED");
  }

  @Test
  public void testPublishError() {
    setUpDccStorageMockService(false);
    val token = "mockToken";
    val id = "AN1";
    assertSongError(() -> service.publish(token, id), UNPUBLISHED_FILE_IDS, null);
  }


  @Test
  public void testSuppress() {
    val an = analysisGenerator.createDefaultRandomAnalysis(SequencingReadAnalysis.class);
    assertThat(an.getAnalysisState()).isEqualTo("UNPUBLISHED");
    val id = an.getAnalysisId();
    service.suppress(id);

    val analysis = service.read(id);
    assertThat(analysis.getAnalysisState()).isEqualTo("SUPPRESSED");
  }

  @Test
  public void testReadFiles() {
    val files = service.readFiles("AN1");
    System.err.printf("Got files '%s'", files);
    val expectedFiles = new ArrayList<File>();

    expectedFiles.add(fileService.read("FI1"));
    expectedFiles.add(fileService.read("FI2"));

    assertThat(files).containsAll(expectedFiles);
    assertThat(expectedFiles).containsAll(files);
  }

  @Test
  public void testDuplicateAnalysisAttemptError() {
    val an1 = service.read("AN1");
    assertSongError(() -> service.create(an1.getStudy(), an1, true),
        DUPLICATE_ANALYSIS_ATTEMPT);
  }

  @Test
  public void testCustomAnalysisId(){
    val study= DEFAULT_STUDY_ID;
    val expectedAnalysisId = "AN-1234";
    val expectedObjectIdMap = Maps.newHashMap();
    expectedObjectIdMap.put("a3bc0998a-3521-43fd-fa10-a834f3874e46.MUSE_1-0rc-vcf.20170711.somatic.snv_mnv.vcf.gz", "0794ae66-80df-5b70-bc22-e49309bfba2a");
    expectedObjectIdMap.put("a3bc0998a-3521-43fd-fa10-a834f3874e46.MUSE_1-0rc-vcf.20170711.somatic.snv_mnv.vcf.gz.idx", "a2449e0a-7020-5f2d-8610-9f58aafd467a" );

    val analysisRaw = payloadGenerator.generateRandomPayload(SequencingReadAnalysis.class,"documents/sequencingread-custom-analysis-id"
        + ".json");
    analysisRaw.setAnalysisId(expectedAnalysisId);
    val actualAnalysisId = service.create(study, analysisRaw, false);
    assertThat(actualAnalysisId).isEqualTo(expectedAnalysisId);
    val analysis = service.read(actualAnalysisId);
    for (val file : analysis.getFile()){
      val filename = file.getFileName();
      assertThat(expectedObjectIdMap).containsKey(filename);
      val expectedObjectId = expectedObjectIdMap.get(filename);
      val actualObjectId = file.getObjectId();
      val actualFileAnalysisId = file.getAnalysisId();
      assertThat(actualObjectId).isEqualTo(expectedObjectId);
      assertThat(actualFileAnalysisId).isEqualTo(actualAnalysisId);
    }
  }

  @Test
  public void testCreateAnalysisStudyDNE(){
    val nonExistentStudyId = randomGenerator.generateRandomUUID().toString();
    assertThat(studyService.isStudyExist(nonExistentStudyId)).isFalse();

    val payload = payloadGenerator.generateDefaultRandomPayload(VariantCallAnalysis.class);
    payload.setAnalysisId(null);

    assertThat(payload.getAnalysisId()).isNull();
    assertSongError(() -> service.create(nonExistentStudyId, payload, false), STUDY_ID_DOES_NOT_EXIST);
  }

  @Test
  public void testGetAnalysisAndIdSearch(){
    val studyGenerator = createStudyGenerator(studyService, randomGenerator);
    val studyId = studyGenerator.createRandomStudy();

    val analysisGenerator = createAnalysisGenerator(studyId, service, payloadGenerator);
    val numAnalysis = 10;
    val sraMap = Maps.<String, SequencingReadAnalysis>newHashMap();
    val vcaMap = Maps.<String, VariantCallAnalysis>newHashMap();
    val expectedAnalyses = Sets.<Analysis>newHashSet();
    for (int i=1; i<=numAnalysis; i++){
      if (i%2 == 0){
        val sra = analysisGenerator.createDefaultRandomSequencingReadAnalysis();
        assertThat(sraMap.containsKey(sra.getAnalysisId())).isFalse();
        sraMap.put(sra.getAnalysisId(), sra);
        expectedAnalyses.add(sra);
      } else {
        val vca = analysisGenerator.createDefaultRandomVariantCallAnalysis();
        assertThat(sraMap.containsKey(vca.getAnalysisId())).isFalse();
        vcaMap.put(vca.getAnalysisId(), vca);
        expectedAnalyses.add(vca);
      }
    }
    assertThat(expectedAnalyses).hasSize(numAnalysis);
    assertThat(sraMap.keySet().size() + vcaMap.keySet().size()).isEqualTo(numAnalysis);
    val expectedVCAs = newHashSet(vcaMap.values());
    val expectedSRAs = newHashSet(sraMap.values());
    assertThat(expectedSRAs).hasSize(sraMap.keySet().size());
    assertThat(expectedVCAs).hasSize(vcaMap.keySet().size());


    val actualAnalyses = service.getAnalysis(studyId);
    val actualSRAs = actualAnalyses.stream()
        .filter(x -> resolveAnalysisType(x.getAnalysisType()) == SEQUENCING_READ)
        .collect(toSet());
    val actualVCAs = actualAnalyses.stream()
        .filter(x -> resolveAnalysisType(x.getAnalysisType()) == VARIANT_CALL)
        .collect(toSet());

    assertThat(actualSRAs).hasSize(sraMap.keySet().size());
    assertThat(actualVCAs).hasSize(vcaMap.keySet().size());
    assertThat(actualSRAs).containsAll(expectedSRAs);
    assertThat(actualVCAs).containsAll(expectedVCAs);

    // Do a study-wide idSearch and verify the response effectively has the same
    // number of results as the getAnalysis method
    val searchedAnalyses = service.idSearch(studyId,
        createIdSearchRequest(null, null, null, null));
    assertThat(searchedAnalyses).hasSameSizeAs(expectedAnalyses);
    assertThat(searchedAnalyses).containsOnlyElementsOf(expectedAnalyses);
  }

  @Test
  public void testGetAnalysisEmptyStudy(){
    val nonExistentStudyId = randomGenerator.generateRandomAsciiString(10);
    assertThat(studyService.isStudyExist(nonExistentStudyId)).isFalse();
    studyService.saveStudy(Study.create(nonExistentStudyId,"","",""));
    assertThat(service.getAnalysis(nonExistentStudyId)).isEmpty();
  }

  @Test
  public void testIdSearchEmptyStudy(){
    val nonExistentStudyId = randomGenerator.generateRandomAsciiString(10);
    assertThat(studyService.isStudyExist(nonExistentStudyId)).isFalse();
    studyService.saveStudy(Study.create(nonExistentStudyId,"","",""));
    val idSearchRequest = createIdSearchRequest(null, null, null, null);
    assertThat(service.idSearch(nonExistentStudyId, idSearchRequest)).isEmpty();
  }

  @Test
  public void testGetAnalysisDNEStudy() {
    val nonExistentStudyId = randomGenerator.generateRandomAsciiString(12);
    assertSongError(() -> service.getAnalysis(nonExistentStudyId), STUDY_ID_DOES_NOT_EXIST);
  }

  @Test
  public void testIdSearchDNEStudy(){
    val nonExistentStudyId = randomGenerator.generateRandomAsciiString(12);
    val idSearchRequest = createIdSearchRequest(null, null, null, null);
    assertSongError(() -> service.idSearch(nonExistentStudyId, idSearchRequest), STUDY_ID_DOES_NOT_EXIST);
  }

  @Test
  public void testAnalysisMissingFilesException(){
    val studyId = DEFAULT_STUDY_ID;

    val analysis1 = analysisGenerator.createDefaultRandomSequencingReadAnalysis();
    val analysisId1 = analysis1.getAnalysisId();

    analysisRepository.deleteFiles(analysisId1);
    assertThat(analysisRepository.readFiles(analysisId1)).isEmpty();
    assertSongError(() -> service.readFiles(analysisId1), ANALYSIS_MISSING_FILES);

    val analysis2 = analysisGenerator.createDefaultRandomVariantCallAnalysis();
    val analysisId2 = analysis2.getAnalysisId();
    analysisRepository.deleteFiles(analysisId2);
    assertThat(analysisRepository.readFiles(analysisId2)).isEmpty();
    assertSongError(() -> service.readFiles(analysisId2), ANALYSIS_MISSING_FILES);
  }

  @Test
  public void testSequencingReadAnalysisMissingSamplesException() {
    runAnalysisMissingSamplesTest(SequencingReadAnalysis.class);
  }

  @Test
  public void testVariantCallAnalysisMissingSamplesException() {
    runAnalysisMissingSamplesTest(VariantCallAnalysis.class);
  }

  @Test
  public void testAnalysisIdDneException(){
    val nonExistentAnalysisId = randomGenerator.generateRandomUUID().toString();
    assertSongError(() -> service.read(nonExistentAnalysisId), ANALYSIS_ID_NOT_FOUND);
    assertSongError(() -> service.readFiles(nonExistentAnalysisId), ANALYSIS_ID_NOT_FOUND);
    assertSongError(() -> service.readSamples(nonExistentAnalysisId), ANALYSIS_ID_NOT_FOUND);
  }

  private void runAnalysisMissingSamplesTest(Class<? extends Analysis> analysisClass) {

    // Create random analysis,
    val analysis = analysisGenerator.createDefaultRandomAnalysis(analysisClass);
    val analysisId = analysis.getAnalysisId();

    analysisRepository.deleteCompositeEntities(analysisId);
    analysis.getSample().stream()
        .map(Sample::getSampleId)
        .forEach(sampleRepository::delete);
    assertSongError(() -> service.readSamples(analysisId), ANALYSIS_MISSING_SAMPLES);
  }

}
