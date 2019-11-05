/*
 * Copyright (c) 2019. Ontario Institute for Cancer Research
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

package bio.overture.song.server.service.id;

import lombok.val;
import org.junit.Test;

import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static bio.overture.song.server.config.IdConfig.createNameBasedGenerator;

public class LocalIdServiceTest {

  private static final String ID_A = "8540ebac-66f2-553a-b865-0d3006edd892";
  private static final String ID_B = "57f844eb-4ab4-5d3d-8dc1-8b7a463e20c1";
  private static final String ID_C = "b4f5aea1-1f4c-5e12-8557-76dbadb26239";

  private static LocalIdService LOCAL_ID_SERVICE= new LocalIdService(createNameBasedGenerator());

  @Test
  public void testDonorId(){
    twoParamTest(LOCAL_ID_SERVICE::getDonorId);
  }

  @Test
  public void testSpecimenId(){
    twoParamTest(LOCAL_ID_SERVICE::getSpecimenId);
  }

  @Test
  public void testSampleId(){
    twoParamTest(LOCAL_ID_SERVICE::getSampleId);
  }

  @Test
  public void testFileId(){
    twoParamTest(LOCAL_ID_SERVICE::getFileId);
  }

  @Test
  public void testAnalysisId(){
    val submittedAnalysisId = UUID.randomUUID().toString();
    val actualId = LOCAL_ID_SERVICE.getAnalysisId(submittedAnalysisId);
    assertEquals(actualId, submittedAnalysisId);
  }

  private void twoParamTest(BiFunction<String, String, String> idServiceFunction){
    val p1_1 = "parameter1_1";
    val p1_2 = "parameter1_2";
    val p2_1 = "parameter2_1";
    val p2_2 = "parameter2_2";
    val id1 = idServiceFunction.apply(p1_1, p2_1);
    assertEquals(id1, ID_A);

    val id2 = idServiceFunction.apply(p1_1, p2_1);
    assertEquals(id2, ID_A);

    val id3 = idServiceFunction.apply(p1_1, p2_2);
    assertEquals(id3, ID_B);

    val id4 = idServiceFunction.apply(p1_2, p2_1);
    assertEquals(id4, ID_C);
  }

}
