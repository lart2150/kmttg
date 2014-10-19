package com.tivo.kmttg.httpserver;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class kmttgServer extends HTTPServer {
   public kmttgServer server;
   
   public kmttgServer() {
      try {
         String baseDir = config.programDir;
         if ( ! file.isDir(baseDir)) {
            log.error("httpserver base directory not found: " + baseDir);
            return;
         }
         server = new kmttgServer(config.httpserver_port);
         VirtualHost host = server.getVirtualHost(null);
         host.setAllowGeneratedIndex(true);
         host.addContext("/", new FileContextHandler(new File(baseDir), "/"));
         server.start();
      } catch (IOException e) {
         log.error("HTTPServer Init - " + e.getMessage());
      }
   }
   
   public kmttgServer(int port) {
      super(port);
   }
   
   // Override parent method to handle special requests
   // Sample rpc request: /rpc?tivo=Roamio&operation=SysInfo
   protected void serve(Request req, Response resp) throws IOException {
      String path = req.getPath();
      
      // Intercept certain special paths, pass along the rest
      if (path.equals("/rpc")) {
         Map<String,String> params = req.getParams();
         if (params.containsKey("operation") && params.containsKey("tivo")) {
            try {
               String operation = params.get("operation");
               String tivo = string.urlDecode(params.get("tivo"));
               JSONObject json;
               if (params.containsKey("json"))
                  json = new JSONObject(string.urlDecode(params.get("json")));
               else
                  json = new JSONObject();
               Remote r = new Remote(tivo);
               if (r.success) {
                  JSONObject result = r.Command(operation, json);
                  if (result != null) {
                     resp.send(200, result.toString());
                  }
                  r.disconnect();
               }
            } catch (Exception e) {
               resp.sendError(500, e.getMessage());
            }
         } else {
            resp.sendError(400, "RPC request missing 'operation' and/or 'tivo'");
            return;
         }
         return;
      }
      
      if (path.equals("/getRpcTivos")) {
         Stack<String> tivos = config.getTivoNames();
         JSONArray a = new JSONArray();
         for (String tivoName : tivos) {
            if (config.rpcEnabled(tivoName))
               a.put(tivoName);
         }
         resp.send(200, a.toString());
         return;
      }
      
      // This is normal/default handling
      ContextHandler handler = req.getVirtualHost().getContext(path);
      if (handler == null) {
          resp.sendError(404);
          return;
      }
      // serve request
      int status = 404;
      // add directory index if necessary
      if (path.endsWith("/")) {
          String index = req.getVirtualHost().getDirectoryIndex();
          if (index != null) {
              req.setPath(path + index);
              status = handler.serve(req, resp);
              req.setPath(path);
          }
      }
      if (status == 404)
          status = handler.serve(req, resp);
      if (status > 0)
          resp.sendError(status);
  }

}