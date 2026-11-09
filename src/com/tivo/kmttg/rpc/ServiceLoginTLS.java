package com.tivo.kmttg.rpc;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

// A middlemind connection on the cdata.p12 certificate with no bodyAuthenticate, for
// serviceLogin, which trades the mobile IdP's SAML assertion for a domain token.
class ServiceLoginTLS extends TiVoRPC {

   ServiceLoginTLS() {
      super(null, config.middlemind_host, null, config.programDir, config.middlemind_port, null, false, false);
   }

   @Override
   protected boolean Auth(String MAK) {
      return true;
   }

   static JSONObject serviceLogin(String samlResponse, String deviceType) throws Exception {
      ServiceLoginTLS rpc = new ServiceLoginTLS();
      try {
         if (! rpc.getSuccess())
            return null;
         JSONObject device = new JSONObject();
         device.put("type", "deviceConfiguration");
         device.put("deviceType", deviceType);
         JSONObject data = new JSONObject();
         data.put("authToken", samlResponse);
         data.put("domain", "tivo");
         data.put("deviceConfiguration", device);
         // middlemind opens with a bodyConfigSearch of its own, addressed to the certificate's
         // body, before it answers ours
         JSONObject r = rpc.SingleRequest("serviceLogin", data);
         for (int i=0; r != null && i<5 && r.optString("type").equals("bodyConfigSearch"); ++i)
            r = rpc.Read();
         return r;
      } finally {
         rpc.disconnect();
      }
   }
}
