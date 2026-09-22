package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The domain token cookie can outlive the JWT inside it, and tivo.com goes by the JWT. Judging
// by the cookie alone meant a token kmttg thought good for days was refused, and never renewed
// because nothing thought it had expired.
public class DomainTokenExpiryTest {

   private static final long HOUR = 3600 * 1000L;
   private String prevToken;
   private long prevExpires;

   @BeforeEach
   public void saveToken() throws Exception {
      prevToken = (String) field("tivo_domain_token").get(null);
      prevExpires = field("tivo_domain_token_expires").getLong(null);
   }

   @AfterEach
   public void restoreToken() {
      config.setDomainToken(prevToken, prevExpires);
   }

   private static Field field(String name) throws Exception {
      Field f = config.class.getDeclaredField(name);
      f.setAccessible(true);
      return f;
   }

   private static String b64(String s) {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
   }

   private static String jwt(long expMillis) {
      return b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}") + "." + b64("{\"sub\":\"x\",\"exp\":" + (expMillis / 1000) + "}")
         + "." + b64("not really a signature");
   }

   @Test
   void aJwtPastItsExpIsExpiredThoughTheCookieIsNot() {
      config.setDomainToken(jwt(System.currentTimeMillis() - HOUR), System.currentTimeMillis() + 24 * HOUR);
      assertTrue(config.isDomainTokenExpired());
   }

   @Test
   void aJwtWithinItsExpIsCurrent() {
      config.setDomainToken(jwt(System.currentTimeMillis() + HOUR), System.currentTimeMillis() + 24 * HOUR);
      assertFalse(config.isDomainTokenExpired());
   }

   @Test
   void theCookieExpiryStillCountsWhenTheJwtWouldAllowIt() {
      config.setDomainToken(jwt(System.currentTimeMillis() + 24 * HOUR), System.currentTimeMillis() - HOUR);
      assertTrue(config.isDomainTokenExpired());
   }

   // Nothing is assumed about the layout, so a token that isn't a JWT at all is judged on the
   // cookie as it always was, rather than as expired.
   @Test
   void aTokenWithNoReadableExpFallsBackToTheCookie() {
      config.setDomainToken("opaque.token.with.some.dots.in", System.currentTimeMillis() + HOUR);
      assertFalse(config.isDomainTokenExpired());
      assertEquals(0, config.jwtExpiry("opaque.token.with.some.dots.in"));
      assertEquals(0, config.jwtExpiry(""));
   }

   @Test
   void theExpIsFoundWhereverItSits() {
      long exp = 1791154018000L;
      String token = "v1." + jwt(exp);
      assertEquals(exp, config.jwtExpiry(token));
   }
}
