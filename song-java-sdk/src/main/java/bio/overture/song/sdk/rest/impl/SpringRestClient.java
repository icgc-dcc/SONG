/*
 * Copyright (c) 2018. Ontario Institute for Cancer Research
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
package bio.overture.song.sdk.rest.impl;

import bio.overture.song.sdk.rest.RestClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.val;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;

@Value
@Builder
public class SpringRestClient implements RestClient {

  @NonNull private final RestTemplate restTemplate;

  @Override
  public <R> ResponseEntity<R> get(@NonNull String endpoint, Class<R> responseType) {
        return restTemplate.exchange(endpoint, GET, null, responseType);
  }

  @Override
  public <R> ResponseEntity<R> post(@NonNull String endpoint, Object body, @NonNull Class<R> responseType) {
    val entity = new HttpEntity<Object>(body, null);
    return restTemplate.postForEntity(endpoint, entity, responseType);
  }

  @Override
  public <R> ResponseEntity<R> put(@NonNull String endpoint, Object body, Class<R> responseType) {
        val entity = new HttpEntity<Object>(body, null);
        return restTemplate.exchange(endpoint, PUT, entity, responseType);
  }

  @Override
  public <R> ResponseEntity<List<R>> putList(String endpoint, Object body, Class<R> responseType) {
    return null;
  }

  @Override public <R> ResponseEntity<List<R>> getList(String endpoint, Class<R> responseType) {
    return null;
  }

  @Override public <R> ResponseEntity<List<R>> postList(String endpoint, Object body, Class<R> responseType) {
    return null;
  }


}
