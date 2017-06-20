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

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.dcc.song.server.model.Upload;
import org.icgc.dcc.song.server.model.analysis.Analysis;
import org.icgc.dcc.song.server.model.enums.IdPrefix;
import org.icgc.dcc.song.server.repository.UploadRepository;
import org.icgc.dcc.song.server.utils.JsonUtils;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import static java.lang.String.format;
import static org.icgc.dcc.song.server.exceptions.Error.error;
import static org.icgc.dcc.song.server.exceptions.ServerErrors.ANALYSIS_ID_NOT_CREATED;
import static org.icgc.dcc.song.server.exceptions.ServerErrors.PAYLOAD_PARSING;
import static org.icgc.dcc.song.server.exceptions.ServerErrors.UPLOAD_ID_NOT_FOUND;
import static org.icgc.dcc.song.server.exceptions.ServerErrors.UPLOAD_ID_NOT_VALIDATED;
import static org.icgc.dcc.song.server.exceptions.ServerErrors.UPLOAD_REPOSITORY_CREATE_RECORD;
import static org.springframework.http.ResponseEntity.ok;

@RequiredArgsConstructor
@Service
@Slf4j
public class UploadService {

  @Autowired
  private final IdService id;
  @Autowired
  private final ValidationService validator;

  @Autowired
  private final AnalysisService analysisService;

  @Autowired
  private final UploadRepository uploadRepository;

  public Upload read(@NonNull String uploadId) {
    return uploadRepository.get(uploadId);
  }

  private void create(@NonNull String studyId, @NonNull String uploadId, @NonNull String jsonPayload) {
    uploadRepository.create(uploadId, studyId, Upload.CREATED, jsonPayload);
  }

  @SneakyThrows
  public ResponseEntity<String> upload(String studyId, String payload) {
    val uploadId = id.generate(IdPrefix.Upload);
    String analysisType;
    try {
      create(studyId, uploadId, payload);
      analysisType = JsonUtils.readTree(payload).at("/analysisType").asText("");
    } catch (UnableToExecuteStatementException jdbie) {
      log.error(jdbie.getCause().getMessage());
      return error(UPLOAD_REPOSITORY_CREATE_RECORD, "[UPLOAD_SERVICE] Unable to create record in upload repository");
    } catch (JsonProcessingException jpe){
      log.error(jpe.getCause().getMessage());
//      throw new ServerException(PAYLOAD_PARSING,
//          "[UPLOAD_SERVICE]: Unable parse the input payload: "+payload, jpe);
      return error(PAYLOAD_PARSING, "[UPLOAD_SERVICE]: Unable parse the input payload: %s ",payload);
    }

    validator.validate(uploadId, payload, analysisType); // Async operation.
    return ok(uploadId);
  }

  public ResponseEntity<String> save(@NonNull String studyId, @NonNull String uploadId) {
    val s = read(uploadId);
    if (s == null ){
      return error(UPLOAD_ID_NOT_FOUND, "UploadId %s does not exist", uploadId);
    }
    val state = s.getState();
    if (!state.equals(Upload.VALIDATED)) {
      return error(UPLOAD_ID_NOT_VALIDATED,
          "UploadId %s is in state '%s', but must be in state 'VALIDATED' before it can be saved.",
          uploadId, state);
    }

    val json = s.getPayload();
    val analysis = JsonUtils.fromJson(json, Analysis.class);
    val analysisId = analysisService.create(studyId, analysis);
    if (analysisId == null) {
      return error(ANALYSIS_ID_NOT_CREATED,"Could not create analysisId for upload id '%s",uploadId);
    }
    updateAsSaved(uploadId);
    return ok(analysisId);
  }

  private void updateAsSaved(@NonNull String uploadId) {
    uploadRepository.update(uploadId, Upload.SAVED, "");
  }

  private ResponseEntity<String> status(HttpStatus status, String format, Object... args) {
    return ResponseEntity
        .status(status)
        .body(format(format, args));
  }



}
