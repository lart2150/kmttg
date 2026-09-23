package com.tivo.kmttg.tools;

import java.io.PrintStream;

// Where the fixture capture reports progress: the console when it runs as a tool, the
// message window when it runs from kmttg's Help menu.
public class CaptureLog {
   public static PrintStream out = System.out;
}
