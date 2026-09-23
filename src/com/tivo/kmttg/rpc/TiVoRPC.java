package com.tivo.kmttg.rpc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Random;
import java.util.Scanner;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.GetKeyStore;
import com.tivo.kmttg.util.log;

/**
 * Establish an RPC connection route with a TiVo using the provided cdata files.
 * Not dependent on anything but java, javax.net.ssl, and com.tivo.kmttg.JSON.JSONObject.
 * Uses either the passed in cdata file with the default password, 
 * or else the cdata.p12 and cdata.password files in the passed in programDir folder.
 *
 */
public class TiVoRPC {
   private boolean debug;
   
   private static final String SchemaVersion = "14";
   private static final String SchemaVersion_newer = "17";
   private static final int DEFAULT_PORT = 1413;
   /**
    * default read timeout in seconds.
    * @see #setSoTimeout(int)
    */
   private static final int timeout = 120;
   
   private Boolean success = true;
   
   protected final String tivoName;
   protected final String IP;
   protected final int port;
   
   private String cdata = null;
   private String programDir;
   
   protected boolean rpcOld;
   // The box's maxMindVersion, asked for only by a request that wants it. kmttg's own
   // requests are written against 17 and go out at it.
   private Integer negotiated = null;

   private int rpc_id = 0;
   private int session_id = 0;
   
   private SSLSocket socket = null;
   private DataInputStream in = null;
   private DataOutputStream out = null;
   private SSLSocketFactory sslSocketFactory = null;

   private int attempt = 0;

   protected void error(String msg) {
      System.err.println("ERROR: "+msg);
   }
   protected void print(String msg) {
      System.out.println(msg);
   }
   protected void warn(String msg) {
      System.out.println("WARNING: "+msg);
   }
   
   public TiVoRPC(String IP, String mak, String programDir) {
      this(null, IP, mak, programDir, -1, null, false, false);
   }
   
   /**
    * Establish an authorized RPC connection.  Check {@link #getSuccess()} for result.
    * @param tivoName the "friendly name" of the TiVo device - not required by default implementation
    * @param IP address to which socket will be connected.
    * @param mak Media Access Key used in authentication
    * @param programDir folder containing cdata files.
    * @param port port to use in connection, 0 or negative to use default.
    * @param cdata filename of cdata file in programDir, null to use defaults.
    * @param oldSchema true if old Schema should be used (automatically gets set to true if new schema fails on first try)
    * @param debug true if debugging should be performed.
    */
   public TiVoRPC(String tivoName, String IP, String mak, String programDir, int port, String cdata, boolean oldSchema, boolean debug) {
      this.cdata = cdata;
      this.programDir = programDir;
      this.rpcOld = oldSchema;
      this.debug = debug;
      this.tivoName = tivoName;
      this.IP = IP;
      if(port <= 0) port = DEFAULT_PORT;
      this.port = port;
      RemoteInit(mak);
   }
   
   /**
    * Test seam: a session over the given streams, with no socket and no authentication.
    * Never used in production code.
    */
   TiVoRPC(InputStream in, OutputStream out) {
      this.tivoName = null;
      this.IP = "";
      this.port = 0;
      this.debug = false;
      this.in = new DataInputStream(in);
      this.out = new DataOutputStream(out);
   }

   public boolean isConnected() {
      return this.socket.isConnected();
   }
   
   /**
    * The result of initialization
    * @return true if the connection was established.
    */
   public boolean getSuccess() {
      return success;
   }
   
   /**
    * calls {@link SSLSocket#setSoTimeout(int)}
    */
   protected void setSoTimeout(int timeout) throws SocketException {
      socket.setSoTimeout(timeout);
   }
   
   /**
    * public method to perform a simple RpcRequest to get a single response.
    * @param type used to set the "RequestType" header, also put into data as "type"
    * @param data if this contains "bodyId" that is used as the "BodyId" header, otherwise that header is blank.
    * @return the JSON response with added IsFinal value, or null if the Write didn't succeed or RpcRequest String had an error.
    */
   public synchronized JSONObject SingleRequest(String type, JSONObject data) {
      String req = RpcRequest(type, false, data);
      if(req != null && Write(req)) {
         return Read();
      } else {
         return null;
      }
   }
   
   /**
    * Define the request String (header and body) to transmit over the socket.
    * SchemaVersion header is defined based on constructor boolean or downgraded automatically on the first error response of "Unsupported schema version."
    * @param type used to set the "RequestType" header, also put into data as "type"
    * @param monitor true to set a "ResponseCount" header of "multiple"
    * @param data if this contains "bodyId" that is used as the "BodyId" header, otherwise that header is blank.
    * @return the String to pass to {@link #Write(String)}
    */
   protected synchronized String RpcRequest(String type, Boolean monitor, JSONObject data) {
      return RpcRequest(type, monitor, data, null);
   }

   // schemaVersion, when given, is the header for this one request, whatever was negotiated.
   protected synchronized String RpcRequest(String type, Boolean monitor, JSONObject data, Integer schemaVersion) {
      try {
         String ResponseCount = "single";
         if (monitor)
            ResponseCount = "multiple";
         String bodyId = "";
         if (data.has("bodyId"))
            bodyId = (String) data.get("bodyId");
         String schema = schemaVersion != null ? schemaVersion.toString() : defaultSchema();
         rpc_id++;
         String eol = "\r\n";
         String headers =
            "Type: request" + eol +
            "RpcId: " + rpc_id + eol +
            "SchemaVersion: " + schema + eol +
            "Content-Type: application/json" + eol +
            "RequestType: " + type + eol +
            "ResponseCount: " + ResponseCount + eol +
            "BodyId: " + bodyId + eol +
            "X-ApplicationName: Quicksilver" + eol +
            "X-ApplicationVersion: 1.2" + eol +
            String.format("X-ApplicationSessionId: 0x%x", session_id) + eol;
         data.put("type", type);

         String body = data.toString();
         String start_line = String.format("MRPC/2 %d %d", headers.length()+2, body.length());
         return start_line + eol + headers + eol + body + "\n";
      } catch (Exception e) {
         error("RpcRequest error: " + e.getMessage());
         return null;
      }
   }
   
   public void disconnect() {
    try {
       if (out != null) out.close();
       if (in != null) in.close();
    } catch (IOException e) {
       error("rpc disconnect error - " + e.getMessage());
    }
   }

   private class NaiveTrustManager implements X509TrustManager {
      // Doesn't throw an exception, so this is how it approves a certificate.
      public void checkClientTrusted ( X509Certificate[] cert, String authType )
                  throws CertificateException {}

      // Doesn't throw an exception, so this is how it approves a certificate.
      public void checkServerTrusted ( X509Certificate[] cert, String authType ) 
         throws CertificateException {}

      public X509Certificate[] getAcceptedIssuers () {
         return new X509Certificate[0];
      }
   }
    
   private final void createSocketFactory() {
      if ( sslSocketFactory == null ) {
        try {
           GetKeyStore getKeyStore = new GetKeyStore(cdata, programDir);
           KeyStore keyStore = getKeyStore.getKeyStore();
           String keyPassword = getKeyStore.getKeyPassword();

           Enumeration<String> aliases = keyStore.aliases();

           while (aliases.hasMoreElements()) {
              String alias = aliases.nextElement();
              X509Certificate crt = (X509Certificate) keyStore.getCertificate(alias);
              LocalDateTime notAfter = crt.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

              int expiresDays = (int) ChronoUnit.DAYS.between(LocalDateTime.now(), notAfter);
              if (expiresDays < 14) {
                 log.error("RPC Certificate expires in " + expiresDays + " days.");
              } else if (expiresDays < 90) {
                 log.warn("RPC Certificate expires in " + expiresDays + " days.");
              }
           }

           KeyManagerFactory fac = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
           fac.init(keyStore, keyPassword.toCharArray());
           SSLContext context = SSLContext.getInstance("TLS");
           TrustManager[] tm = new TrustManager[] { new NaiveTrustManager() };
           context.init(fac.getKeyManagers(), tm, new SecureRandom());
           sslSocketFactory = context.getSocketFactory();
        } catch (KeyManagementException e) {
          error("KeyManagementException - " + e.getMessage()); 
        } catch (NoSuchAlgorithmException e) {
          error("NoSuchAlgorithmException - " + e.getMessage());
        } catch (KeyStoreException e) {
           error("KeyStoreException - " + e.getMessage());
        } catch (FileNotFoundException e) {
           error("FileNotFoundException - " + e.getMessage());
        } catch (CertificateException e) {
           error("CertificateException - " + e.getMessage());
        } catch (IOException e) {
           error("IOException - " + e.getMessage());
        } catch (UnrecoverableKeyException e) {
           error("UnrecoverableKeyException - " + e.getMessage());
        }
      }
    }

    /** perform a (non-web) socket setup and auth.  should be followed by If getSuccess() bodyId_get() */
    private void RemoteInit(String MAK) {
       createSocketFactory();
       //TODO this is going to produce the exact same session_id in every instance.  Should seed Random with e.g. the current time.
       session_id = new Random(0x27dc20).nextInt();
       try {
          socket = (SSLSocket) sslSocketFactory.createSocket(IP, port);
          socket.setNeedClientAuth(true);
          socket.setEnableSessionCreation(true);
          socket.setSoTimeout(timeout*1000);
          socket.startHandshake();
          in = new DataInputStream(socket.getInputStream());
          out = new DataOutputStream(socket.getOutputStream());
          
          success = Auth(MAK);

       } catch (Exception e) {
          if (attempt == 0 && e.getMessage() != null && e.getMessage().contains("UNKNOWN ALERT")) {
             // Try it again as this could be temporary glitch
             attempt = 1;
             warn("RemoteInit 2nd attempt...");
             RemoteInit(MAK);
             return;
          }
          error("RemoteInit - (IP=" + IP + ", port=" + port + "): " + e.getMessage());
          error(Arrays.toString(e.getStackTrace()));
          success = false;
       }
    }


    /**
     * default implementation: perform a bodyAuthenticate RPC request to the connected device
     * @param MAK the makCredential to use in the bodyAuthenticate
     * @return true if response status equals "success"
     */
   protected boolean Auth(String MAK) {
       try {
          JSONObject credential = new JSONObject();
          JSONObject h = new JSONObject();
          credential.put("type", "makCredential");
          credential.put("key", MAK);
          h.put("credential", credential);
          String req = RpcRequest("bodyAuthenticate", false, h);
          if (Write(req) ) {
             JSONObject result = Read();
             if (result.has("status")) {
                if (result.get("status").equals("success"))
                   return true;
             }
          }
       } catch (Exception e) {
          error("rpc Auth error - " + e.getMessage());
       }
       return false;
    }
    
   /**
    * Write the request to the socket. 
    * @param data
    * @return true if the write succeeded
    */
   protected synchronized final boolean Write(String data) {
      try {
         if (debug) {
            print("WRITE: " + data);
         }
         if (out == null)
            return false;
         out.write(data.getBytes());
         out.flush();
      } catch (IOException e) {
         error("rpc Write error - " + e.getMessage());
         return false;
      }
      return true;
   }
   
   /**
    * Read the response after a Write.
    * If the response type was "error" and the error was a "Unsupported schema version", sets rpcOld. 
    * Adds the boolean header "IsFinal" as a value in the response.
    * @return the JSON response.
    */
   protected synchronized final JSONObject Read() {
      try {
         String[] message = readMessage();
         if (message == null)
            return null;
         JSONObject j = new JSONObject(message[1]);
         if (noteError(j))
            fallBack(Integer.parseInt(defaultSchema()));
         // not returning null for an error.  subclasses can make that choice.
         j.put("IsFinal", message[0].contains("IsFinal: true"));
         return j;
      } catch (Exception e) {
         error("rpc Read error - " + e.getMessage());
         return null;
      }
   }

   /**
    * Write a request and read its reply, as {@link #Read()} returns it.
    * A reply refusing the SchemaVersion the request went out at steps this connection down,
    * and the request is sent again at the version it stepped to - unless mayFallBack is false,
    * as it is for a request sent at a version the caller chose.
    */
   protected synchronized final JSONObject Request(String req, boolean mayFallBack) {
      Reply reply = exchange(req, mayFallBack);
      if (reply == null)
         return null;
      if (reply.json == null) {
         error("rpc Read error - reply is not JSON");
         return null;
      }
      try {
         reply.json.put("IsFinal", reply.headers.contains("IsFinal: true"));
      } catch (JSONException e) {
         error("rpc Read error - " + e.getMessage());
         return null;
      }
      return reply.json;
   }

   // As Request, but the reply body exactly as the TiVo sent it, error or not.
   protected synchronized final String RequestRaw(String req, boolean mayFallBack) {
      Reply reply = exchange(req, mayFallBack);
      return reply == null ? null : reply.body;
   }

   // A reply off the socket, parsed once. json is null when the body isn't JSON.
   private static class Reply {
      final String headers, body;
      final JSONObject json;

      Reply(String[] message) {
         headers = message[0];
         body = message[1];
         JSONObject j = null;
         try {
            j = new JSONObject(body);
         } catch (JSONException e) {
            // Not JSON - still the TiVo's answer.
         }
         json = j;
      }
   }

   private Reply exchange(String req, boolean mayFallBack) {
      try {
         while (true) {
            if (! Write(req))
               return null;
            String[] message = readMessage();
            if (message == null)
               return null;
            Reply reply = new Reply(message);
            boolean refused = reply.json != null && noteError(reply.json);
            if (! refused || ! mayFallBack || ! fallBack(SchemaVersions.of(req)))
               return reply;
            req = SchemaVersions.withSchema(req, Integer.parseInt(defaultSchema()));
         }
      } catch (Exception e) {
         error("rpc Read error - " + e.getMessage());
         return null;
      }
   }

   private String defaultSchema() {
      return rpcOld ? SchemaVersion : SchemaVersion_newer;
   }

   // The version to send a request at when it wants the box's newest rather than kmttg's 17:
   // the web endpoints' passthrough, and the few requests 17 doesn't define. A newer grammar
   // can change what a request does, not just add fields - gridRowSearch ignores its anchor
   // channel at 42 - so nothing of kmttg's own goes out at it.
   synchronized int negotiatedSchema() {
      if (rpcOld)
         return Integer.parseInt(SchemaVersion);
      if (negotiated == null)
         negotiateSchema();
      return negotiated != null ? negotiated : Integer.parseInt(SchemaVersion_newer);
   }

   // Ask the box how high it goes, once per TiVo per run. Software that predates
   // maxMindVersion refuses the probe or leaves the field out, and either way stays at 17.
   void negotiateSchema() {
      if (rpcOld)
         return;
      Integer known = SchemaVersions.cached(schemaKey());
      if (known == null) {
         try {
            JSONObject data = new JSONObject();
            data.put("bodyId", "-");
            String req = RpcRequest("bodyConfigSearch", false, data, SchemaVersions.PROBE);
            if (req == null || ! Write(req)) {
               success = false;
               return;
            }
            String[] message = readMessage();
            if (message == null) {
               success = false;
               return;
            }
            known = Math.max(Integer.parseInt(SchemaVersion_newer), SchemaVersions.maxMindVersion(message[1]));
         } catch (Exception e) {
            // Replies on the LAN are read in order, not matched by RpcId, so a probe that timed
            // out would have its late answer read as the reply to the next request. The
            // connection is no use after that.
            error("SchemaVersion probe failed - " + e.getMessage());
            success = false;
            return;
         }
         SchemaVersions.remember(schemaKey(), known);
         log.print(schemaName() + ": SchemaVersion " + known);
      }
      negotiated = known;
   }

   // Down a step from the version a refused request went out at: from the negotiated version
   // to 17, from 17 to 14. False when there is no lower step to take.
   private boolean fallBack(int sentAt) {
      if (rpcOld)
         return false;
      int newer = Integer.parseInt(SchemaVersion_newer);
      if (sentAt > newer) {
         log.warn(schemaName() + ": SchemaVersion " + sentAt + " refused - using " + newer);
         negotiated = newer;
         SchemaVersions.remember(schemaKey(), newer);
      } else {
         // Revert to older schema version for older TiVo software versions
         warn("Reverting to older RPC schema version.");
         rpcOld = true;
      }
      return true;
   }

   private String schemaKey() {
      return IP + ":" + port;
   }

   private String schemaName() {
      return tivoName != null ? tivoName : IP;
   }

   // One message off the socket as {headers, body}. Expects a start line of the form
   // "MRPC/2 76 1870": header length, then body length.
   @SuppressWarnings("deprecation")
   private String[] readMessage() throws IOException {
      String buf = in.readLine();
      if (debug) {
         print("READ: " + buf);
      }
      if (buf == null || ! buf.matches("^.*MRPC/2.+$"))
         return null;
      String[] split = buf.split(" ");
      byte[] headers = new byte[Integer.parseInt(split[1])];
      readBytes(headers, headers.length);
      byte[] body = new byte[Integer.parseInt(split[2])];
      readBytes(body, body.length);
      if (debug) {
         print("READ: " + new String(headers) + new String(body));
      }
      return new String[] { new String(headers, "UTF8"), new String(body, "UTF8") };
   }
   
   // Logs an error reply, and says whether it is the TiVo refusing the SchemaVersion.
   private boolean noteError(JSONObject j) throws JSONException {
      if (j.has("type") && j.getString("type").equals("error")) {
         error("RPC error response:\n" + j.toString(3));
         return SchemaVersions.isUnsupported(j);
      }
      return false;
   }

   private void readBytes(byte[] body, int len) throws IOException {
      int bytesRead = 0;
      while (bytesRead < len) {
         int n = in.read(body, bytesRead, len - bytesRead);
         // -1 is the TiVo closing mid message. Adding it in walked the count backwards until
         // read threw on a negative offset, which reported the close as an index error.
         if (n < 0)
            throw new EOFException("connection closed part way through a response");
         bytesRead += n;
      }
   }
   
}
