package com.tivo.kmttg.main;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
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
   
   public static InputStream getNowPlaying(final String urlString, final String username, final String password) {
      InputStream in = null;
      Authenticator authenticator = new Authenticator() {
         protected PasswordAuthentication getPasswordAuthentication() {
             return new PasswordAuthentication(username, password.toCharArray());
         }
      };
      try {
         URL url = new URL(urlString);
         URLConnection conn = getConnection(url);         
         
         // Set authentication and get input stream
         synchronized (Authenticator.class) {
            Authenticator.setDefault(authenticator);
            in = conn.getInputStream();
            Authenticator.setDefault(null);
         }
      }
      catch (MalformedURLException e) {
         log.error("http Malformed URL exception: " + urlString);
         log.error(e.getMessage());
      }
      catch (IOException e) {
         log.error("http IO exception: " + e.getMessage());
      }
      catch (Exception e) {
         log.error("getConnection error: " + e.getMessage());
      }
      return in;
   }
   
   public static InputStream getTivoStream(final String urlString, final String username, final String password) {
      int TIMEOUT = 10;
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
         conn.setReadTimeout(TIMEOUT*1000);
         // NOTE: Intentionally connect without authentication to grab cookies
         try { conn.connect(); }
         catch (IOException ignore) {};
         cm.storeCookies(conn);
         
         // Connect again and init cookies with connection
         conn = getConnection(url);
         cm.setCookies(conn);
         
         // Set authentication and get input stream
         synchronized (Authenticator.class) {
            Authenticator.setDefault(authenticator);
            in = conn.getInputStream();
            Authenticator.setDefault(null);
         }
      }
      catch (MalformedURLException e) {
         log.error("http Malformed URL exception: " + urlString);
         log.error(e.getMessage());
      }
      catch (IOException e) {
         log.error("http IO exception: " + e.getMessage());
      }
      catch (Exception e) {
         log.error("getConnection error: " + e.getMessage());
      }

      return in;
   }

}
