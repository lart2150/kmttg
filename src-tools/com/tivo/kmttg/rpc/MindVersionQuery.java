package com.tivo.kmttg.rpc;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * The newest RPC grammar a TiVo will accept - its maxMindVersion - for the fixture capture
 * tools to record beside the fixtures.
 *
 * A box only reports it when it is asked at a level nearly that new. bodyConfig carries
 * maxMindVersion from SchemaVersion 19 on; kmttg itself talks 17, and at 17 the box leaves
 * the field out of the answer altogether rather than refusing the request - which is why a
 * plain bodyConfigSearch through Remote comes back without it. Below 19 there is nothing to
 * ask for, so the request goes out at exactly that.
 *
 * TiVoRPC's schema version is a private compile-time constant, so the header is rewritten
 * on the way out rather than configured. Only the query is raised: the handshake still goes
 * out at kmttg's own version, which is the one level every box kmttg supports is known to
 * accept.
 *
 * Measured on a Bolt running 21.11.1.v22-USC-11-849, which answers 42 at 19 and above and
 * nothing at 18 and below.
 */
public class MindVersionQuery extends TiVoRPC {

   private static final String SCHEMA = "19";

   // Set once the handshake is done, so only the query that needs it is raised.
   private boolean raised = false;

   private MindVersionQuery(String tivoName, String IP, String mak, String programDir) {
      super(tivoName, IP, mak, programDir, -1, null, false, false);
   }

   /**
    * @return the box's maxMindVersion, or null if it would not give one - which is the
    * answer for a box too old to know the field, and for one that is not listening.
    */
   public static String get(String tivoName, String ip, String mak, String programDir) {
      MindVersionQuery q = new MindVersionQuery(tivoName, ip, mak, programDir);
      try {
         if (! q.getSuccess()) return null;
         q.raised = true;
         JSONObject json = new JSONObject();
         // "-" rather than the service number, the way bodyId_get asks before it knows one.
         json.put("bodyId", "-");
         JSONObject response = q.SingleRequest("bodyConfigSearch", json);
         if (response == null || ! response.has("bodyConfig")) return null;
         JSONArray configs = response.getJSONArray("bodyConfig");
         if (configs.length() == 0) return null;
         // A number rather than a string, so it is read as an Object and not with getString.
         Object version = configs.getJSONObject(0).opt("maxMindVersion");
         return version == null ? null : String.valueOf(version);
      } catch (Exception e) {
         System.out.println("  maxMindVersion: " + e.getMessage());
         return null;
      } finally {
         q.disconnect();
      }
   }

   // Same layout super builds - start line, headers, blank line, body - with the schema
   // version swapped and the start line's two lengths recomputed to match it.
   @Override
   protected synchronized String RpcRequest(String type, Boolean monitor, JSONObject data) {
      String req = super.RpcRequest(type, monitor, data);
      if (req == null || ! raised) return req;
      String rest = req.substring(req.indexOf("\r\n") + 2);
      int end = rest.indexOf("\r\n\r\n");
      String headers = rest.substring(0, end + 2)
         .replaceAll("SchemaVersion: \\d+\r\n", "SchemaVersion: " + SCHEMA + "\r\n");
      String body = rest.substring(end + 4, rest.length() - 1);
      return String.format("MRPC/2 %d %d", headers.length() + 2, body.length())
         + "\r\n" + headers + "\r\n" + body + "\n";
   }

}
