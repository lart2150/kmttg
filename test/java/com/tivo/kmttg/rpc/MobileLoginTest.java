package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import com.tivo.kmttg.JSON.JSONObject;

// The mobile IdP only answers an AuthnRequest naming the app's issuer and ACS, and the
// assertion it sends back has to quote our ID.
public class MobileLoginTest {

   @Test
   public void authnRequestIsTheAppsOwn() throws Exception {
      String xml = GetDomainToken.mobileAuthnRequest("_123", Instant.parse("2026-11-08T12:34:56.789Z"));
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setNamespaceAware(true);
      Element root = f.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
      assertEquals("AuthnRequest", root.getLocalName());
      assertEquals("_123", root.getAttribute("ID"));
      assertEquals("2026-11-08T12:34:56Z", root.getAttribute("IssueInstant"));
      assertEquals("http://tivomobile-acs/sso", root.getAttribute("AssertionConsumerServiceURL"));
      assertEquals("tivomobile-acs", root.getElementsByTagNameNS("*", "Issuer").item(0).getTextContent());
   }

   // domainTokenValidateInterval is seconds: the app stores it times 1000
   @Test
   public void opaqueTokenLastsTheValidateInterval() throws Exception {
      long now = 1_000_000L;
      JSONObject info = new JSONObject("{\"domainToken\":\"opaque\",\"domainTokenValidateInterval\":1044221}");
      assertEquals(now + 1044221L*1000, GetDomainToken.mobileTokenExpiry(info, now));
   }

   @Test
   public void noIntervalGetsADay() throws Exception {
      long now = 1_000_000L;
      JSONObject info = new JSONObject("{\"domainToken\":\"opaque\"}");
      assertEquals(now + GetDomainToken.MOBILE_TOKEN_LIFE, GetDomainToken.mobileTokenExpiry(info, now));
      assertTrue(GetDomainToken.MOBILE_TOKEN_LIFE > 0);
   }
}
