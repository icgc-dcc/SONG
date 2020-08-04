package bio.overture.song.server.security;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.isNull;

@RequiredArgsConstructor
public class PublicKeyStore {

  /**
   * Dependencies
   */
  @NonNull private final String url;
  @NonNull private final RestTemplate restTemplate;
  @NonNull private final RetryTemplate retryTemplate;

  /**
   * Cached public key
   */
  private final AtomicReference<String> cachedPublicKey = new AtomicReference<>();

  public String read(){
    String key = cachedPublicKey.get();
    if (isNull(key)){
      return updateAndRead();
    }
    return key;
  }

  public String updateAndRead(){
    cachedPublicKey.set(fetchPublicKey());
    return cachedPublicKey.get();
  }

  //  // TODO: [rtisma] add error handling
  private String fetchPublicKey(){
    val resp = retryTemplate.execute(x -> restTemplate.getForEntity(url, String.class));
    return resp.hasBody() ? resp.getBody() : null;
  }

}
