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
 */
package org.icgc.dcc.sodalite.server.model;

import java.util.Map;
import java.util.TreeMap;

import org.icgc.dcc.sodalite.server.utils.JsonUtils;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import lombok.val;

public class Metadata {

  private final Map<String, Object> metadata = new TreeMap<>();

  @JsonAnySetter
  public void setMetadata(String key, Object value) {
    metadata.put(key, value);
  }

  @SuppressWarnings("unchecked")
  public void addMetadata(String json) {
    if (json != null && !json.equals("")) {
      Map<String, Object> m;
      try {
        m = JsonUtils.fromJson(json, Map.class);
      } catch (IllegalArgumentException e) {
        val j = JsonUtils.ObjectNode().put("metadata", json);
        m = JsonUtils.convertValue(j, Map.class);
      }
      metadata.putAll(m);
    }
  }

  public String getMetadata() {
    return JsonUtils.toJson(metadata);
  }
}
