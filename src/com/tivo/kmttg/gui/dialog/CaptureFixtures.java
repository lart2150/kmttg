package com.tivo.kmttg.gui.dialog;

import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JOptionPane;
import javax.swing.JTextArea;

import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.tools.CaptureLog;
import com.tivo.kmttg.tools.ContributorCapture;
import com.tivo.kmttg.util.log;

// Help > Capture TiVo test data: the contributor capture, run from inside kmttg. Says what
// the zip will hold before anything is read, and reports progress to the message window.
public class CaptureFixtures {
   private static final AtomicBoolean running = new AtomicBoolean(false);

   public static void promptUser() {
      if (running.get()) {
         log.warn("A TiVo test data capture is already running");
         return;
      }
      String intro =
           "Reads your TiVos and writes a zip of test data for kmttg's developers, so\n"
         + "they can check kmttg against your model and software version. One TiVo of\n"
         + "each model is read, which can take a few minutes on a large My Shows list.\n"
         + "\n"
         + "The zip is saved in " + config.programDir + "\n"
         + "and nothing is sent anywhere.\n"
         + "\n";
      JTextArea text = new JTextArea(intro + ContributorCapture.CONTENTS);
      text.setEditable(false);
      text.setOpaque(false);
      text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
      Object[] options = {"Capture", "Cancel"};
      int choice = JOptionPane.showOptionDialog(
         config.gui.getFrame(), text, "Capture TiVo test data",
         JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
         null, options, options[0]
      );
      if (choice == 0)
         run();
   }

   private static void run() {
      if (! running.compareAndSet(false, true))
         return;
      new Thread(new Runnable() {
         @Override public void run() {
            PrintStream previous = CaptureLog.out;
            File zip = null;
            try {
               CaptureLog.out = new PrintStream(new LogStream(), true, "UTF-8");
               log.warn(">> Capturing TiVo test data...");
               zip = ContributorCapture.run(new File(config.configIni), new File(config.programDir));
            } catch (Exception e) {
               log.error("TiVo test data capture failed - " + e.getMessage());
            } finally {
               CaptureLog.out = previous;
               running.set(false);
            }
            final File written = zip;
            SwingUtil.runLater(new Runnable() {
               @Override public void run() {
                  if (written == null) {
                     JOptionPane.showMessageDialog(config.gui.getFrame(),
                        "Nothing was captured - see the message window for why.",
                        "Capture TiVo test data", JOptionPane.WARNING_MESSAGE);
                  } else {
                     JOptionPane.showMessageDialog(config.gui.getFrame(),
                        "Written:\n" + written.getAbsolutePath()
                        + "\n\nOpen the zip and look it over before sending it on.",
                        "Capture TiVo test data", JOptionPane.INFORMATION_MESSAGE);
                  }
               }
            });
         }
      }).start();
   }

   // Hands each line the capture prints to the message window
   private static class LogStream extends OutputStream {
      private final ByteArrayOutputStream line = new ByteArrayOutputStream();

      @Override public void write(int b) {
         if (b == '\n') {
            log.print(new String(line.toByteArray(), StandardCharsets.UTF_8).replace("\r", ""));
            line.reset();
         } else {
            line.write(b);
         }
      }
   }
}
