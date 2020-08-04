package bio.overture.song.server.controller;

import bio.overture.song.server.security.JWTTokenConverter;
import bio.overture.song.server.security.PublicKeyStore;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import javax.transaction.Transactional;

import static org.mockito.Mockito.when;

@Slf4j
@Transactional
@SpringBootTest
@RunWith(SpringRunner.class)
@ActiveProfiles({"test", "jwt"})
public class JWTSecurityTest {

  @Autowired
  private JWTTokenConverter jwtTokenConverter;
  private PublicKeyStore mockPublicKeyStore;


  @Before
  public void beforeTest(){
    this.mockPublicKeyStore = Mockito.mock(PublicKeyStore.class);
    ReflectionTestUtils.setField(jwtTokenConverter, "publicKeyFetcher", mockPublicKeyStore);
    when(mockPublicKeyStore.read()).thenReturn("something");
  }

  @Test
  public void testRob(){
    log.info("sdfsdf");
  }


}
