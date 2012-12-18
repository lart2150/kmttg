package com.tivo.kmttg.mind;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class EasySSLHelper {
   public static class TrustEverythingTrustManager implements X509TrustManager {
      public java.security.cert.X509Certificate[] getAcceptedIssuers() {
         return null;
      }

      public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {   }

      public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {   }
   }


   public static class VerifyEverythingHostnameVerifier implements HostnameVerifier {

      public boolean verify(String string, SSLSession sslSession) {
         return true;
      }
   }

   private static SSLSocketFactory sslSocketFactory;

   /**
    * Returns a SSL Factory instance that accepts all server certificates.
    * <pre>SSLSocket sock =
    *     (SSLSocket) getSocketFactory.createSocket ( host, 443 ); </pre>
    * @return  An SSL-specific socket factory. 
    **/
   public synchronized static final SSLSocketFactory getSocketFactory()
   {
      if ( sslSocketFactory == null ) {
         try {
            TrustManager[] tm = new TrustManager[] { new TrustEverythingTrustManager() };
            SSLContext context = SSLContext.getInstance ("SSL");
            context.init( new KeyManager[0], tm, new SecureRandom( ) );

            sslSocketFactory = (SSLSocketFactory) context.getSocketFactory ();

         } catch (KeyManagementException e) {
            //log.error ("No SSL algorithm support: " + e.getMessage(), e); 
         } catch (NoSuchAlgorithmException e) {
            //log.error ("Exception when setting up the Naive key management.", e);
         }
      }
      return sslSocketFactory;
   }

   public static void install() {
      TrustManager[] trustManager = new TrustManager[] {new TrustEverythingTrustManager()};

      // Let us create the factory where we can set some parameters for the connection
      SSLContext sslContext = null;
      try {
         sslContext = SSLContext.getInstance("SSL");
         sslContext.init(null, trustManager, new java.security.SecureRandom());
      } catch (NoSuchAlgorithmException e) {
         // do nothing
      }catch (KeyManagementException e) {
         // do nothing
      }

      HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());     
   }

   public static URLConnection openEasySSLConnection(URL url) throws IOException {
      URLConnection con = url.openConnection();
      if (con instanceof HttpsURLConnection) {
         HttpsURLConnection conssl = (HttpsURLConnection) con;
         //conssl.setSSLSocketFactory(E)
         conssl.setHostnameVerifier(new EasySSLHelper.VerifyEverythingHostnameVerifier());        
         conssl.setSSLSocketFactory(getSocketFactory());

      }
      return con;
   }
}