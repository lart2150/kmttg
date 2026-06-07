package com.tivo.kmttg.rpc;

import java.util.ArrayList;
import java.util.List;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * A {@link Remote} that connects to a live TiVo (like the normal Remote) but
 * additionally records every {@link #Command(String, JSONObject)} it issues as
 * a {type, request, response} triple. Used by {@link RpcFixtureCapture} to
 * capture the full RPC request/response trace of a high-level read call so the
 * call can later be replayed offline through the real Remote logic (see
 * {@link ReplayRemote}).
 *
 * NOT a unit test - this only runs during fixture capture against a live TiVo.
 */
public class RecordingRemote extends Remote {
   private final List<JSONObject> log = new ArrayList<>();
   private boolean recording = true;

   public RecordingRemote(String tivoName, String IP, int port, String MAK, String cdata) {
      super(tivoName, IP, port, MAK, cdata);
   }

   /** Turn command recording on/off (e.g. off for the huge MyShows trace). */
   public void setRecording(boolean on) {
      recording = on;
   }

   /** Discard everything recorded so far (call before a method you want to log). */
   public void reset() {
      log.clear();
   }

   /** The recorded command trace as a JSONArray of {type, request, response}. */
   public JSONArray log() {
      JSONArray a = new JSONArray();
      for (JSONObject o : log)
         a.put(o);
      return a;
   }

   @Override
   public JSONObject Command(String type, JSONObject json) {
      // Copy the request BEFORE super.Command mutates it (it adds uri/parameters/etc.).
      JSONObject request;
      try {
         request = (json == null) ? new JSONObject() : new JSONObject(json.toString());
      } catch (Exception e) {
         request = new JSONObject();
      }
      JSONObject result = super.Command(type, json);
      if (!recording)
         return result;
      try {
         JSONObject rec = new JSONObject();
         rec.put("type", type);
         rec.put("request", request);
         if (result != null)
            rec.put("response", result);
         log.add(rec);
      } catch (Exception e) {
         // recording is best-effort; never break a capture over it
      }
      return result;
   }
}
