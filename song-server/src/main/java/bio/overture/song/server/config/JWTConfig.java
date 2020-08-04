package bio.overture.song.server.config;

import bio.overture.song.server.security.CustomResourceServerTokenServices;
import bio.overture.song.server.security.JWTTokenConverter;
import bio.overture.song.server.security.PublicKeyStore;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.web.client.RestTemplate;

@Configuration
public class JWTConfig {

  private final String publicKeyUrl;
  private final RetryTemplate retryTemplate;
  private final RemoteTokenServices remoteTokenServices;

  @Autowired
  public JWTConfig(
      @NonNull @Value("${auth.jwt.public-key-url}") String publicKeyUrl,
      @NonNull RemoteTokenServices remoteTokenServices,
      @NonNull RetryTemplate retryTemplate){
    this.publicKeyUrl = publicKeyUrl;
    this.retryTemplate = retryTemplate;
    this.remoteTokenServices = remoteTokenServices;
  }

  @Bean
  @Primary
  public CustomResourceServerTokenServices customResourceServerTokenServices(@Autowired JWTTokenConverter jwtTokenConverter) {
    return new CustomResourceServerTokenServices(
        remoteTokenServices, buildJwtTokenStore(jwtTokenConverter), retryTemplate);
  }

  private JwtTokenStore buildJwtTokenStore(JWTTokenConverter jwtTokenConverter) {
    return new JwtTokenStore(jwtTokenConverter);
  }

  @Bean
  public JWTTokenConverter jwtTokenConverter() {
    return new JWTTokenConverter(publicKeyFetcher());
  }

  // TODO: rtisma --- ideally, this public key fetching is more dynamic. For instance, if EGO
  // changes its public key,
  // this song server needs to be rebooted, meaning downtime. Would be better if the public key is
  // cached, and
  // when a request fails, try to update cache and if there is a new value, update cache and try
  // again, otherwise
  // error out as normal.
  private String getPublicKey() {
    val rest = new RestTemplate();
    // TODO: [rtisma] add error handling
    ResponseEntity<String> response =
        retryTemplate.execute(x -> rest.getForEntity(publicKeyUrl, String.class));
    return response.getBody();
  }

  public PublicKeyStore publicKeyFetcher(){
    return new PublicKeyStore(publicKeyUrl, new RestTemplate(), retryTemplate);
  }
}
