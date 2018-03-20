package org.icgc.dcc.song.client.benchmark;

import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.codehaus.jackson.map.ObjectMapper;
import org.icgc.dcc.song.client.benchmark.model.UploadData;
import org.icgc.dcc.song.client.benchmark.monitor.BenchmarkMonitor;
import org.icgc.dcc.song.client.benchmark.monitor.CounterMonitor;
import org.icgc.dcc.song.client.benchmark.monitor.StudyMonitor;
import org.icgc.dcc.song.client.config.Config;
import org.icgc.dcc.song.client.register.Registry;
import org.icgc.dcc.song.client.register.RestClient;
import org.icgc.dcc.song.core.utils.JsonUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Thread.sleep;
import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;
import static org.icgc.dcc.song.client.benchmark.rest.StudyClient.createStudyClient;
import static org.icgc.dcc.song.client.benchmark.monitor.BenchmarkMonitor.createBenchmarkMonitor;
import static org.icgc.dcc.song.core.utils.JsonUtils.readTree;

@Slf4j
@RequiredArgsConstructor(access = PRIVATE)
public class Benchmark {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  @NonNull private final BenchmarkConfig benchmarkConfig;

  /**
   * State
   */
  private BenchmarkMonitor monitor;

  public static void main(String[] args){
    Benchmark benchmark = null;
    val accessToken = "something";
    val serverUrl = "http://song.cancercollaboratory.org:8080";
    val inputDataDir = Paths.get("some_input_path");
    val benchmarkConfig = BenchmarkConfig.builder()
        .accessToken(accessToken)
        .serverUrl(serverUrl)
        .inputDataDir(inputDataDir)
        .allowStudyCreation(true)
        .ignoreIdCollisions(true)
//        .includeStudy("PRAD-UK")
//        .includeStudy("BRCA-UK")
//        .includeStudy("SKCA-BR")
        .build();
    benchmark = Benchmark.createBenchmark(benchmarkConfig);
    benchmark.run();
    benchmark.genReport(Paths.get("./report.json"));
  }

  private Config createConfig(String studyId){
    val config = new Config();
    config.setAccessToken(benchmarkConfig.getAccessToken());
    config.setProgramName("song");
    config.setDebug("true");
    config.setServerUrl(benchmarkConfig.getServerUrl());
    config.setStudyId(studyId);
    return config;
  }

  public void run(){
    val payloadFileVistor = extractData(benchmarkConfig);
    monitor = createBenchmarkMonitor(payloadFileVistor.getStudies());
    upload(payloadFileVistor);
    monitor.getUploadMonitors().forEach(CounterMonitor::displaySummary);
    waitForStatus(payloadFileVistor);
    save(payloadFileVistor);
  }

  public void upload(PayloadFileVisitor payloadFileVisitor){
    for (val studyId : payloadFileVisitor.getStudies()){
      setupStudy(studyId);
      val config = createConfig(studyId);
      val restClient = new RestClient(config.restTemplate());
      val registry = new Registry(config, restClient);
      checkState(registry.isAlive(), "The song server '%s' is not running", benchmarkConfig.getAccessToken());
      val uploadDatas = payloadFileVisitor.getDataForStudy(studyId);
      uploadStudy(studyId, registry, uploadDatas);
    }
  }

  public void waitForStatus(PayloadFileVisitor payloadFileVisitor){
    for (val studyId : payloadFileVisitor.getStudies()){
      val config = createConfig(studyId);
      val restClient = new RestClient(config.restTemplate());
      val registry = new Registry(config, restClient);
      val uploadDatas = payloadFileVisitor.getDataForStudy(studyId);
      waitForStatusForStudy(studyId,registry, uploadDatas);
    }
  }

  public void save(PayloadFileVisitor payloadFileVisitor){
    for(val studyId : payloadFileVisitor.getStudies()){
      val config = createConfig(studyId);
      val restClient = new RestClient(config.restTemplate());
      val registry = new Registry(config, restClient);
      val uploadDatas = payloadFileVisitor.getDataForStudy(studyId);
      saveForStudy(studyId,registry, uploadDatas);
    }
  }

  @SneakyThrows
  public void genReport(Path outputFile){
    Files.createDirectories(outputFile.getParent());
    val stats = monitor.getStudies().stream()
        .map(s -> monitor.getStudyMonitor(s))
        .map(StudyMonitor::getStatComposite)
        .collect(Collectors.toList());
    MAPPER.writeValue(outputFile.toFile(),stats);
  }

  @SneakyThrows
  private void saveForStudy( String studyId, Registry registry, List<UploadData> uploadDataList){
    val modList = uploadDataList.stream()
        .filter(x -> !x.getUploadState().equals("VALIDATION_ERROR"))
        .collect(toList());
    val total = modList.size();
    val errored = uploadDataList.size() - total;
    log.warn("Num Errored Files: {}", errored);
    int count = 0;
    val saveMonitor = monitor.getStudyMonitor(studyId).getSaveMonitor();

    for (val uploadData : modList) {
      val submittedAnalysisId = uploadData.getAnalysisId();
      val idExists = doesIdExist(uploadData.getStudyId(), registry, submittedAnalysisId);
      log.info("ANALYSIS_ID {} existence: {}", submittedAnalysisId, idExists);
      val rawResponse = saveMonitor.callIncr(() -> registry.save(
          uploadData.getStudyId(),uploadData.getUploadId(), benchmarkConfig.isIgnoreIdCollisions() ));
      val response = readTree(rawResponse.getOutputs());
      val status = response.path("status").textValue();
      val analysisId = response.path("analysisId").textValue();
      uploadData.setAnalysisId(analysisId);
      log.info("Saving {} file {} /{} : {}",uploadData.getStudyId(), ++count, total, uploadData.getFile().getFileName
          ().toString());
    }
  }

  public boolean doesIdExist(String studyId, Registry registry, String analysisId){
    val status = registry.getAnalysis(studyId, analysisId);
    return !status.hasErrors();
  }

  @SneakyThrows
  private void waitForStatusForStudy(String studyId, Registry registry, List<UploadData> dataList){
    int count = 0 ;
    val statusMonitor = monitor.getStudyMonitor(studyId).getStatusMonitor();
    do{
      count = 0;
      for (val uploadData : dataList) {
        val resp = statusMonitor.callIncr(() -> registry.getUploadStatus(uploadData.getStudyId(), uploadData
                .getUploadId()));
        val uploadResponse =  readTree(resp.getOutputs());
        val state = uploadResponse.path("state").textValue();
        val errors = Lists.<String>newArrayList();
        uploadResponse.path("errors").iterator().forEachRemaining(x -> errors.add(x.textValue()));
        uploadData.setUploadState(state);
        uploadData.setUploadErrors(errors);
        uploadData.setSubmittedAnalysisId(uploadData.getAnalysisId());
        if (state.equals("VALIDATED") || state.equals("VALIDATION_ERROR")) {
          continue;
        }
        count++;
        val sleepTimeS = 1;
        log.info("Sleeping {} seconds", sleepTimeS);
        sleep(sleepTimeS*1000);
      }
    } while (count > 0);

  }

  @SneakyThrows
  private void uploadStudy(String studyId, Registry registry, List<UploadData> datas){
    int count = 0;
    int total = datas.size();
    val uploadMonitor = monitor.getStudyMonitor(studyId).getUploadMonitor();
    for (val uploadData : datas){
      val node = JsonUtils.mapper().readTree(uploadData.getFile().toFile());
      val payload = JsonUtils.toJson(node);
      val submittedAnalysisId = node.path("analysisId").textValue();
      val rawResponse = uploadMonitor.callIncr(() -> registry.upload(payload, true));
      val response = readTree(rawResponse.getOutputs());
      val status= response.path("status").textValue();
      val uploadId = response.path("uploadId").textValue();
      uploadData.setUploadId(uploadId);
      uploadData.setAnalysisId(submittedAnalysisId);
      uploadData.setSubmittedAnalysisId(submittedAnalysisId);
      log.info("Uploaded {} file {} / {}: {}",
          uploadData.getStudyId(), ++count, total, uploadData.getFile().toString());
    }
  }

  @SneakyThrows
  private static PayloadFileVisitor extractData(BenchmarkConfig benchmarkConfig){
    val rootDir = benchmarkConfig.getInputDataDir();
    val payloadFileVisitor = new PayloadFileVisitor(rootDir,
        benchmarkConfig.getExcludeStudies(),
        benchmarkConfig.getIncludeStudies());
    Files.walkFileTree(rootDir, payloadFileVisitor);
    return payloadFileVisitor;
  }

  private void setupStudy(String studyId){
    val client = createStudyClient(benchmarkConfig.getAccessToken(), benchmarkConfig.getServerUrl());
    val studyExist = client.isStudyExist(studyId);
    if(!studyExist){
      checkState(benchmarkConfig.isAllowStudyCreation(),
          "Cannot create the study '%s' becuase study creation is disabled",
          studyId);
      client.saveStudy(studyId, "ICGC", "", "");
    }
  }

  public static Benchmark createBenchmark( @NonNull BenchmarkConfig benchmarkConfig) {
    return new Benchmark(benchmarkConfig);
  }


}
