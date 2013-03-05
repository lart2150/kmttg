package com.tivo.kmttg.main;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.tivo.kmttg.util.log;

public class http {
   private static final int READ_TIMEOUT = 120;  // Timeout for InputStream reads
   private static final SSLSocketFactory TRUST_ANY = createSocketFactory();
   
   private static final HostnameVerifier VERIFY_ANY = new HostnameVerifier() {
      public boolean verify(String hostname, SSLSession session) {
         return true;
      }
   };
   
   private static SSLSocketFactory createSocketFactory() {
      TrustManager trustAny = new X509TrustManager() {
          public void checkClientTrusted(X509Certificate[] certs, String authType) { }
          public void checkServerTrusted(X509Certificate[] certs, String authType) { }
          public X509Certificate[] getAcceptedIssuers() {
              return null;
          }
      };
      try {
          SSLContext context = SSLContext.getInstance("SSL");
          context.init(null, new TrustManager[] { trustAny }, new SecureRandom());
          return context.getSocketFactory();
      } catch (Exception ex) {
         log.print("SSL Error: " + ex.getMessage());
         return null;
      }
  }
  
   private static URLConnection getConnection(URL url) throws Exception {
      URLConnection connection = url.openConnection();
      if (connection instanceof HttpsURLConnection) {
         HttpsURLConnection conn = (HttpsURLConnection) connection;
         conn.setHostnameVerifier(VERIFY_ANY);
         if (TRUST_ANY != null) {
             conn.setSSLSocketFactory(TRUST_ANY);
         }
      }
      return connection;
   }
   
   private static InputStream noCookieInputStream(final String urlString, final String username, final String password, final String offset) {
      InputStream in = null;
      Authenticator authenticator = new Authenticator() {
         protected PasswordAuthentication getPasswordAuthentication() {
             return new PasswordAuthentication(username, password.toCharArray());
         }
      };
      try {
         URL url = new URL(urlString);
         URLConnection conn = getConnection(url);         
         conn.setReadTimeout(READ_TIMEOUT*1000);
         if (offset != null) {
            conn.setRequestProperty("Range", "bytes=" + offset + "-");
         }
         
         // Set authentication and get input stream
         synchronized (Authenticator.class) {
            Authenticator.setDefault(authenticator);
            in = conn.getInputStream();
            Authenticator.setDefault(null);
         }
      }
      catch (MalformedURLException e) {
         log.error("http Malformed URL exception for: " + urlString);
         log.error(e.getMessage());
      }
      catch (IOException e) {
         log.error("http IO exception for: " + urlString);
         log.error(e.getMessage());
      }
      catch (Exception e) {
         log.error("getConnection error for: " + urlString);
         log.error(e.getMessage());
      }
      return in;
   }
   
   private static InputStream cookieInputStream(final String urlString, final String username, final String password, final String offset) {
      InputStream in = null;
      CookieManager cm = new CookieManager();
      Authenticator authenticator = new Authenticator() {
         protected PasswordAuthentication getPasswordAuthentication() {
             return new PasswordAuthentication(username, password.toCharArray());
         }
      };
      try {
         URL url = new URL(urlString);
         URLConnection conn = getConnection(url);
         // NOTE: Intentionally connect without authentication to grab cookies
         try { conn.connect(); }
         catch (IOException ignore) {};
         cm.storeCookies(conn);
         // Print header information
         /*String header = conn.getHeaderField(0);
         log.print(header);
         log.print("---Start of headers---");
         int i = 1;
         while ((header = conn.getHeaderField(i)) != null) {
             String key = conn.getHeaderFieldKey(i);
             log.print(((key==null) ? "" : key + ": ") + header);
             i++;
         }
         log.print("---End of headers---");*/
         
         // Connect again and init cookies with connection
         conn = getConnection(url);
         conn.setReadTimeout(READ_TIMEOUT*1000);
         if (offset != null) {
            conn.setRequestProperty("Range", "bytes=" + offset + "-");
         }
         cm.setCookies(conn);
         
         // Set authentication and get input stream
         synchronized (Authenticator.class) {
            Authenticator.setDefault(authenticator);
            in = conn.getInputStream();
            Authenticator.setDefault(null);
         }
      }
      catch (MalformedURLException e) {
         log.error("http Malformed URL exception for: " + urlString);
         log.error(e.getMessage());
      }
      catch (IOException e) {
         log.error("http IO exception for: " + urlString);
         log.error(e.getMessage());
      }
      catch (Exception e) {
         log.error("getConnection error for: " + urlString);
         log.error(e.getMessage());
      }

      return in;
   }
   
   public static Boolean download(String url, String username, String password, String outFile, Boolean cookies, String offset)
      throws IOException, InterruptedException, Exception {
      InputStream in;
      if (cookies)
         in = http.cookieInputStream(url, username, password, offset);
      else
         in = http.noCookieInputStream(url, username, password, offset);
      if (in == null) {
         return false;
      } else {
         int BUFSIZE = 65536;
         byte[] buffer = new byte[BUFSIZE];
         int c;
         FileOutputStream out = null;
         try {
            out = new FileOutputStream(outFile);
            while ((c = in.read(buffer, 0, BUFSIZE)) != -1) {
               if (Thread.interrupted()) {
                  out.close();
                  in.close();
                  throw new InterruptedException("Killed by user");
               }
               out.write(buffer, 0, c);
            }
            out.close();
            in.close();
         }
         catch (FileNotFoundException e) {
            log.error(url + ": " + e.getMessage());
            if (out != null) out.close();
            if (in != null) in.close();
            throw new FileNotFoundException(e.getMessage());
         }
         catch (IOException e) {
            log.error(url + ": " + e.getMessage());
            if (out != null) out.close();
            if (in != null) in.close();
            throw new IOException(e.getMessage());
         }
         catch (Exception e) {
            log.error(url + ": " + e.getMessage());
            if (out != null) out.close();
            if (in != null) in.close();
            throw new Exception(e.getMessage(), e);
         }
         finally {
            if (out != null) out.close();
            if (in != null) in.close();
         }
      }

      return true;
   }
   
   public static Boolean downloadPiped(String url, String username, String password, OutputStream out, Boolean cookies, String offset)
      throws IOException, InterruptedException, Exception {
      InputStream in;
      if (cookies)
         in = http.cookieInputStream(url, username, password, offset);
      else
         in = http.noCookieInputStream(url, username, password, offset);
      if (in == null)
         return false;
      
      int BUFSIZE = 65536;
      byte[] buffer = new byte[BUFSIZE];
      int c;
      try {
         while ((c = in.read(buffer, 0, BUFSIZE)) != -1) {
            if (Thread.interrupted()) {
               out.close();
               in.close();
               throw new InterruptedException("Killed by user");
            }
            out.write(buffer, 0, c);
         }
         out.close();
         in.close();
      }
      catch (FileNotFoundException e) {
         log.error(url + ": " + e.getMessage());
         if (out != null) out.close();
         if (in != null) in.close();
         throw new FileNotFoundException(e.getMessage());
      }
      catch (IOException e) {
         log.error(url + ": " + e.getMessage());
         if (out != null) out.close();
         if (in != null) in.close();
         throw new IOException(e.getMessage());
      }
      catch (Exception e) {
         log.error(url + ": " + e.getMessage());
         if (out != null) out.close();
         if (in != null) in.close();
         throw new Exception(e.getMessage(), e);
      }
      finally {
         if (out != null) out.close();
         if (in != null) in.close();
      }

      return true;
   }
   
   // Check URL is alive with specificed connection timeout
   public static Boolean isAlive(String urlString, int timeout) {
      try {
         URL url = new URL(urlString);
         URLConnection conn = getConnection(url);
         conn.setConnectTimeout(timeout*1000);
         conn.connect();
      } catch (Exception e) {
         log.error("isAlive: " + urlString + " - " + e.getMessage());
         return false;
      }
      return true;
   }
   
   public static String getLocalhostIP() {
      try {
         InetAddress localhost = InetAddress.getLocalHost();
         return localhost.getHostAddress();
      } catch (UnknownHostException e) {
         log.error("getLocalhostIP - " + e.getMessage());
      }
      return null;
   }

}
