package com.tivo.kmttg.rpc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.tivo.kmttg.JSON.JSONObject;

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
      try {
         String ResponseCount = "single";
         if (monitor)
            ResponseCount = "multiple";
         String bodyId = "";
         if (data.has("bodyId"))
            bodyId = (String) data.get("bodyId");
         String schema = SchemaVersion_newer;
         if (rpcOld)
            schema = SchemaVersion;
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
           KeyStore keyStore = KeyStore.getInstance("PKCS12");
           // This is default USA password
           String password = "vlZaKoduom"; // expires 2024
           //String password = "XF7x4714qw"; // expires 12/11/2022
           //String password = "5vPNhg6sV4tD"; // expires 12/18/2020
           InputStream keyInput;
           if (cdata == null) {
              // Installation dir cdata.p12 file takes priority if it exists
              String cdata = programDir + "/cdata.p12";
              if ( new File(cdata).isFile() ) {
                 keyInput = new FileInputStream(cdata);
                 cdata = programDir + "/cdata.password";
                 if (new File(cdata).isFile()) {
                    Scanner s = new Scanner(new File(cdata));
                    password = s.useDelimiter("\\A").next();
                    s.close();
                 } else {
                    error("cdata.p12 file present, but cdata.password is not");
                 }
              } else {
                 // Read default USA cdata.p12 from kmttg.jar
                 keyInput = getClass().getResourceAsStream("/cdata.p12");
              }
           }
           else
              keyInput = new FileInputStream(cdata);
           keyStore.load(keyInput, password.toCharArray());
           keyInput.close();
           KeyManagerFactory fac = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
           fac.init(keyStore, password.toCharArray());
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
   @SuppressWarnings("deprecation")
   protected synchronized final JSONObject Read() {
      String buf = "";
      Integer head_len;
      Integer body_len;
      
      try {
         // Expect line of format: MRPC/2 76 1870
         // 1st number is header length, 2nd number body length
         buf = in.readLine();
         if (debug) {
            print("READ: " + buf);
         }
         if (buf != null && buf.matches("^.*MRPC/2.+$")) {
            String[] split = buf.split(" ");
            head_len = Integer.parseInt(split[1]);
            body_len = Integer.parseInt(split[2]);
            
            byte[] headers = new byte[head_len];
            readBytes(headers, head_len);
   
            byte[] body = new byte[body_len];
            readBytes(body, body_len);
            
            if (debug) {
               print("READ: " + new String(headers) + new String(body));
            }
            
            // Pull out IsFinal value from header
            Boolean IsFinal;
            buf = new String(headers, "UTF8");
            if (buf.contains("IsFinal: true"))
               IsFinal = true;
            else
               IsFinal = false;
            
            // Return json contents with IsFinal flag added
            buf = new String(body, "UTF8");
            JSONObject j = new JSONObject(buf);
            if (j.has("type") && j.getString("type").equals("error")) {
               error("RPC error response:\n" + j.toString(3));
               if (j.has("text") && j.getString("text").equals("Unsupported schema version")) {
                  // Revert to older schema version for older TiVo software versions
                  warn("Reverting to older RPC schema version - try command again.");
                  rpcOld = true;
               }
               // not returning null.  subclasses can make that choice.
            }
            j.put("IsFinal", IsFinal);
            return j;

         }
      } catch (Exception e) {
         error("rpc Read error - " + e.getMessage());
         return null;
      }
      return null;
   }

   private void readBytes(byte[] body, int len) throws IOException {
      int bytesRead = 0;
      while (bytesRead < len) {
         bytesRead += in.read(body, bytesRead, len - bytesRead);
      }
   }
   
}
