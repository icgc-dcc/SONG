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

import static org.icgc.dcc.song.server.model.enums.IdPrefix.Specimen;

import java.util.List;

import lombok.NonNull;
import org.icgc.dcc.song.server.model.entity.Specimen;
import org.icgc.dcc.song.server.model.enums.IdPrefix;
import org.icgc.dcc.song.server.repository.SpecimenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
@Service
public class SpecimenService {

  @Autowired
  IdService idService;
  @Autowired
  SpecimenRepository repository;
  @Autowired
  SampleService sampleService;

  public String create(@NonNull String parentId, @NonNull Specimen specimen) {
    val id = idService.generate(Specimen);
    specimen.setSpecimenId(id);
    specimen.setDonorId(parentId);
    int status =
        repository.create(specimen);
    if (status != 1) {
      return "error: Can't create" + specimen.toString();
    }
    specimen.getSamples().forEach(s -> sampleService.create(id, s));
    return "ok:" + id;
  }

  public Specimen read(@NonNull String id) {
    val specimen = repository.read(id);
    if (specimen == null) {
      return null;
    }
    specimen.setSamples(sampleService.readByParentId(id));
    return specimen;
  }

  public List<Specimen> readByParentId(@NonNull String parentId) {
    val specimens = repository.readByParentId(parentId);
    specimens.forEach(s -> s.setSamples(sampleService.readByParentId(s.getSpecimenId())));
    return specimens;
  }

  public String update(@NonNull Specimen specimen) {
    repository.update(specimen);
    return "ok";
  }

  public String delete(@NonNull String id) {
    sampleService.deleteByParentId(id);
    repository.delete(id);
    return "ok";
  }

  public String deleteByParentId(@NonNull String parentId) {
    repository.findByParentId(parentId).forEach(this::delete);
    return "ok";
  }

  public List<String> findByParentId(@NonNull String donorId) {
    return repository.findByParentId(donorId);
  }

  public String findByBusinessKey(@NonNull String studyId, @NonNull String submitterId) {
    return repository.findByBusinessKey(studyId, submitterId);
  }

  public String save(@NonNull String studyId, @NonNull Specimen specimen) {
    String specimenId = repository.findByBusinessKey(studyId, specimen.getSpecimenSubmitterId());
    if (specimenId == null) {
      specimenId = idService.generate(IdPrefix.Specimen);
      specimen.setSpecimenId(specimenId);
      repository.create(specimen);
    } else {
      specimen.setSpecimenId(specimenId);
      repository.update(specimen);
    }
    return specimenId;
  }

}
