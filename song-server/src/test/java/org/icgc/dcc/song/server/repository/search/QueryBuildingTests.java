package org.icgc.dcc.song.server.repository.search;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class QueryBuildingTests {

  private static final String BEGINNING_PORTION = "SELECT analysis.id AS analysis_id ";
  private static final String INCLUDED_INFO_PORTION = ", info.info AS info " ;
  private static final String MIDDLE_PORTION =
      "FROM analysis INNER JOIN info ON analysis.id = info.id WHERE info.id_type = 'Analysis'";
  private static final String WITH_CONDITIONS = " AND info.info->>'key1' ~ '.*value1$' AND info.info->'key2'->'key3'->>'key4' ~ '" + ".*value2\\d+';";
  private static final String WITHOUT_CONDITIONS = ";";

  @Test
  public void testSearchQueryEmptyNoInfo(){
    val query = createQuery(false, true);
    assertThat(query).isEqualTo(BEGINNING_PORTION+MIDDLE_PORTION+WITHOUT_CONDITIONS);
  }

  @Test
  public void testSearchQueryEmptyIncludeInfo(){
    val query = createQuery(true, true);
    assertThat(query).isEqualTo(BEGINNING_PORTION+INCLUDED_INFO_PORTION+MIDDLE_PORTION+WITHOUT_CONDITIONS);
  }

  @Test
  public void testSearchQueryBasicIncludeInfo(){
    val query = createQuery(true, false);
    assertThat(query).isEqualTo(BEGINNING_PORTION+INCLUDED_INFO_PORTION+MIDDLE_PORTION+WITH_CONDITIONS);
  }

  @Test
  public void testSearchQueryBasicNoInfo(){
    val query = createQuery(false, false);
    assertThat(query).isEqualTo(BEGINNING_PORTION+MIDDLE_PORTION+WITH_CONDITIONS);
  }

  private static String createQuery(boolean includeInfoField, boolean isEmpty){
    val searchQueryBuilder = new SearchQueryBuilder(includeInfoField);
    if (!isEmpty){
      searchQueryBuilder.add("key1", ".*value1$");
      searchQueryBuilder.add("key2.key3.key4", ".*value2\\d+");
    }
    val query = searchQueryBuilder.build();
    log.debug(query);
    return query;
  }

}