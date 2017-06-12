package org.icgc.dcc.song.server.service;

import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.dcc.common.core.util.Joiners;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static java.lang.Boolean.parseBoolean;
import static lombok.AccessLevel.PRIVATE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Service
@Slf4j
public class ExistenceService {

  private static final String UPLOAD = "upload";

  private static final int DEFAULT_TIMEOUT = 2;

  @Autowired
  private RetryTemplate retryTemplate;

  @NonNull private final String storageUrl;

  @Setter(PRIVATE)
  private int timeout;
  private RestTemplate restTemplate = new RestTemplate();

  public ExistenceService(RetryTemplate retryTemplate, String storageUrl) {
    this.storageUrl = joinUrl(storageUrl, UPLOAD);
    this.retryTemplate = retryTemplate;
    setTimeout(DEFAULT_TIMEOUT);
  }

  @SneakyThrows
  public boolean isObjectExist(@NonNull String accessToken, @NonNull String objectId) {
    return retryTemplate.execute(retryContext -> {
      val endpointUrl = joinUrl(storageUrl, objectId);
      val url = new URL(endpointUrl);
      val httpHeaders = new HttpHeaders();
      httpHeaders.set(AUTHORIZATION, accessToken);
      val req = new HttpEntity<>(httpHeaders);
      val resp = restTemplate.exchange(url.toURI(), HttpMethod.GET, req, String.class);
      return parseBoolean(resp.getBody());
    });
  }


  public static ExistenceService createExistenceService(RetryTemplate retryTemplate, String storageUrl, int timeout){
    val e = createExistenceService(retryTemplate, storageUrl);
    e.setTimeout(timeout);
    return e;
  }

  public static ExistenceService createExistenceService(RetryTemplate retryTemplate,String baseUrl){
    return new ExistenceService(retryTemplate,baseUrl);
  }

  @SneakyThrows
  private static BufferedReader getReader(HttpURLConnection con){
    return new BufferedReader(new InputStreamReader(con.getInputStream()));
  }

  private static String joinUrl(String ... path){
    return Joiners.SLASH.join(path);
  }

}
