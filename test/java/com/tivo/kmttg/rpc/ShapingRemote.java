package com.tivo.kmttg.rpc;

import java.util.ArrayList;
import java.util.List;

import com.tivo.kmttg.JSON.JSONObject;

/**
 * An offline {@link Remote} that captures what {@link Remote#Command} builds
 * instead of sending it.
 *
 * {@link ReplayRemote} overrides Command outright, so the whole request-shaping
 * half of Command - the sixty-branch chain that decides the wire request name
 * and folds in bodyId, the recording state, the subscribe wrapper - never runs
 * under it. That half is the only thing standing between a menu click and the
 * TiVo doing something different from what was asked, and none of it is
 * otherwise reachable offline.
 *
 * This double hooks one level lower: {@link #RpcRequest} is where Command hands
 * off its finished request, so overriding it records the wire request name and
 * the shaped JSON. Returning null from there stops Command before the socket
 * write (there is no socket) - it logs one "unhandled Key type" line and
 * returns null, which is expected and not a failure.
 *
 * What is captured is byte for byte what -rpcLog writes, so a recorded request
 * from a capture is the expected value: it is a request a real TiVo accepted.
 */
public class ShapingRemote extends Remote {

   /** One captured hand-off: the wire request name, the shaped request, and
    *  whether it asked the socket to keep reading responses. */
   public static class Sent {
      public final String rpc;
      public final JSONObject json;
      /** monitor=true becomes "ResponseCount: multiple" in the RPC headers. */
      public final boolean monitor;

      Sent(String rpc, JSONObject json, boolean monitor) {
         this.rpc = rpc;
         this.json = json;
         this.monitor = monitor;
      }
   }

   private final List<Sent> sent = new ArrayList<Sent>();

   public ShapingRemote() throws Exception {
      super(); // no-connect seam
      // Several branches fold bodyId_get() into the request; seed it so it
      // answers from config instead of issuing a bodyConfigSearch.
      Fixtures.seedBodyId();
   }

   /** The most recent request, or null if Command never got as far as sending. */
   public Sent last() {
      return sent.isEmpty() ? null : sent.get(sent.size() - 1);
   }

   public int count() {
      return sent.size();
   }

   @Override
   public String RpcRequest(String rpc, Boolean monitor, JSONObject json) {
      try {
         // The real transport stamps the wire request name onto the body as
         // "type" before serializing, and -rpcLog captures the object after
         // that, so do it here too - it is what makes a captured request
         // directly comparable to a shaped one.
         json.put("type", rpc);
         // Command mutates the request in place, so snapshot it.
         sent.add(new Sent(rpc, new JSONObject(json.toString()), Boolean.TRUE.equals(monitor)));
      } catch (Exception e) {
         throw new IllegalStateException("could not snapshot the " + rpc + " request", e);
      }
      return null; // no transport - stops Command before the socket write
   }
}
