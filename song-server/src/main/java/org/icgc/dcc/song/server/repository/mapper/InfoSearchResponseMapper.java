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
package org.icgc.dcc.song.server.repository.mapper;

import lombok.SneakyThrows;
import lombok.val;
import org.icgc.dcc.song.server.repository.search.InfoSearchResponse;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.icgc.dcc.song.core.utils.JsonUtils.readTree;
import static org.icgc.dcc.song.server.model.enums.InfoSearchResponseColumns.analysis_id;
import static org.icgc.dcc.song.server.model.enums.InfoSearchResponseColumns.info;
import static org.icgc.dcc.song.server.repository.search.InfoSearchResponse.createWithInfo;
import static org.icgc.dcc.song.server.repository.search.InfoSearchResponse.createWithoutInfo;

public class InfoSearchResponseMapper implements ResultSetMapper<InfoSearchResponse> {

  private static final int MIN_COLUMN_COUNT = 1;

  @Override
  @SneakyThrows
  public InfoSearchResponse map(int index, ResultSet r, StatementContext ctx) throws SQLException {
    val columnCount = r.getMetaData().getColumnCount();
    if (columnCount > MIN_COLUMN_COUNT){
      return createWithInfo(r.getString(analysis_id.name()), readTree(r.getString(info.name())));
    } else {
      return createWithoutInfo(r.getString(analysis_id.name()));
    }
  }

}