package bio.overture.song.server.service.id;

import bio.overture.song.core.utils.ResourceFetcher;
import bio.overture.song.server.properties.IdProperties.FederatedProperties.UriTemplateProperties;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static bio.overture.song.core.utils.Joiners.COMMA;
import static bio.overture.song.core.utils.ResourceFetcher.ResourceType.TEST;
import static bio.overture.song.server.service.id.UriResolver.createUriResolver;

@Slf4j
public class UriResolverTest {

  private static final String GOOD_FIXTURE_FILENAME = "good.json";
  private static final ResourceFetcher RESOURCE_FETCHER = ResourceFetcher.builder()
      .resourceType(TEST)
      .dataDir(Paths.get("documents/uriResolver"))
      .build();

  @Test
  public void uriResolver_goodFixture_success(){
    assertGood(loadResolver(GOOD_FIXTURE_FILENAME));
  }

  @Test
  public void testUnknownTemplateVariable(){
    val good = loadFixture("good.json");
    val bad = loadFixture("good.json");

    // Test donor
    bad.setDonor(good.getDonor()+"&something={someVar}");
    assertOnlyUnknownVariables(bad, "someVar");
    bad.setDonor(good.getDonor());

    // Test specimen
    bad.setSpecimen(good.getSpecimen()+"&something={someVar}");
    assertOnlyUnknownVariables(bad, "someVar");
    bad.setSpecimen(good.getSpecimen());

    // Test sample
    bad.setSample(good.getSample()+"&something={someVar}");
    assertOnlyUnknownVariables(bad, "someVar");
    bad.setSample(good.getSample());

    // Test file
    bad.setFile(good.getFile()+"&something={someVar}");
    assertOnlyUnknownVariables(bad, "someVar");
    bad.setFile(good.getFile());

    // Test analysis.existence
    bad.getAnalysis().setExistence(good.getAnalysis().getExistence()+"&something={someVar}&sss={someVar2}");
    assertOnlyUnknownVariables(bad, "someVar", "someVar2");
    bad.getAnalysis().setExistence(good.getAnalysis().getExistence());

    // Test analysis.save
    bad.getAnalysis().setSave(good.getAnalysis().getSave()+"&something={someVar}&sss={someVar2}");
    assertOnlyUnknownVariables(bad, "someVar", "someVar2");
    bad.getAnalysis().setSave(good.getAnalysis().getSave());

    // Test analysis.generate
    bad.getAnalysis().setGenerate(good.getAnalysis().getGenerate()+"&something={someVar}");
    assertOnlyUnknownVariables(bad, "someVar");
    bad.getAnalysis().setGenerate(good.getAnalysis().getGenerate());
  }

  @Test
  public void testMissingTemplateVariable(){
    val good = loadFixture("good.json");
    val bad = loadFixture("good.json");

    // Test donor
    bad.setDonor("https://example.org/proj={studyId}");
    assertOnlyMissingVariables(bad, "submitterId");
    bad.setDonor(good.getDonor());

    // Test specimen
    bad.setSpecimen("https://example.org/proj={studyId}");
    assertOnlyMissingVariables(bad, "submitterId");
    bad.setSpecimen(good.getSpecimen());

    // Test sample
    bad.setSample("https://example.org/proj={studyId}");
    assertOnlyMissingVariables(bad, "submitterId");
    bad.setSample(good.getSample());

    // Test file
    bad.setFile("https://example.org/an={analysisId}");
    assertOnlyMissingVariables(bad, "fileName");
    bad.setFile(good.getFile());

    // Test analysis.existence
    bad.getAnalysis().setExistence("https://example.org/existence");
    assertOnlyMissingVariables(bad, "analysisId");
    bad.getAnalysis().setExistence(good.getAnalysis().getExistence());

    // Test analysis.save
    bad.getAnalysis().setSave("https://example.org/save");
    assertOnlyMissingVariables(bad, "analysisId");
    bad.getAnalysis().setSave(good.getAnalysis().getSave());
  }

  private static void assertOnlyMissingVariables(UriTemplateProperties p, String ... variables ){
    assertExceptionThrown(() -> createUriResolver(p), IllegalArgumentException.class,
        containsString(format("Missing template variables: [%s]", COMMA.join(variables))),
        not(containsString("Unknown template variables")));
  }

  private static void assertOnlyUnknownVariables(UriTemplateProperties p, String ... variables ){
    assertExceptionThrown(() -> createUriResolver(p), IllegalArgumentException.class,
        containsString(format("Unknown template variables: [%s]", COMMA.join(variables))),
        not(containsString("Missing template variables")));
  }

  private static UriResolver loadResolver(@NonNull String fixtureFilename){
    val p = loadFixture(fixtureFilename);
    return createUriResolver(p);
  }

  private static  void assertGood(UriResolver ur){
    assertEquals("https://example.org?sid=subDonor123&proj=ABC123-CA",
        ur.expandDonorUri("ABC123-CA", "subDonor123"));

    assertEquals("https://example.org?sid=subSpecimen123&proj=ABC123-CA",
        ur.expandSpecimenUri("ABC123-CA", "subSpecimen123"));

    assertEquals("https://example.org?sid=subSample123&proj=ABC123-CA",
        ur.expandSampleUri("ABC123-CA", "subSample123"));

    assertEquals("https://example.org?anid=AN01&fname=my-file.vcf.gz",
        ur.expandFileUri("AN01", "my-file.vcf.gz"));

    assertEquals("https://example.org?aid=AN01", ur.expandAnalysisExistenceUri("AN01"));

    assertEquals("https://example.org?aid=AN01", ur.expandAnalysisSaveUri("AN01"));

    assertEquals("https://example.org/generate", ur.expandAnalysisGenerateUri());
  }

  private static void assertExceptionThrown(@NonNull Supplier s, @NonNull Class<? extends Throwable> exceptionClass, Matcher<String> ... stringMatchers){
    try {
      s.get();
      fail(format("Expected an exception to be thrown"));
    } catch (Throwable e){
      if (!exceptionClass.isInstance(e)){
        fail(format("Expected exception of type '%s', but the actual exception was of type '%s'",
            exceptionClass.getSimpleName(), e.getClass().getSimpleName()));
      }
      val message = e.getMessage();
      stream(stringMatchers).forEach(m -> assertThat(message, m));
    }
  }

  @SneakyThrows
  private static UriTemplateProperties loadFixture(@NonNull String basename){
    RESOURCE_FETCHER.check();
    return RESOURCE_FETCHER.readObject(basename, UriTemplateProperties.class);
  }

}
