package com.tivo.kmttg.task;

import com.tivo.kmttg.util.backgroundProcess;

public abstract class baseTask {
   // Required override methods
   public abstract Boolean launchJob();
   public abstract Boolean start();
   public abstract Boolean check();
   public abstract backgroundProcess getProcess();
   public abstract void kill();
}
