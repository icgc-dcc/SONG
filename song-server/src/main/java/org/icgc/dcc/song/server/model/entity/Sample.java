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

package org.icgc.dcc.song.server.model.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.val;
import org.icgc.dcc.song.server.model.Metadata;
import org.icgc.dcc.song.server.model.enums.Constants;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Sample extends Metadata {

  private String sampleId = "";
  private String specimenId="";
  private String sampleSubmitterId = "";
  private String sampleType = "";

  public static Sample create(String id, @NonNull String submitter, String specimenId, String type) {
    val sample = new Sample();
    sample.setSampleId(id);
    sample.setSpecimenId(specimenId);
    sample.setSampleSubmitterId(submitter);

    sample.setSampleType(type);

    return sample;
  }

  public void setSampleType(String type) {
    Constants.validate(Constants.SAMPLE_TYPE, type);
    sampleType = type;
  }


}
