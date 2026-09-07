package com.tivo.kmttg.gui.swing;

import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.tivo.kmttg.main.config;

/**
 * Shared plumbing for the GUI tests.
 *
 * Swing components belong to the event dispatch thread, and these tests pack
 * and resize windows that the EDT is laying out at the same time, so the work
 * goes through run(). Theme also writes global look and feel state - the fonts
 * survive a look and feel swap, so a test that raises the font size leaves
 * every later test measuring 27 pixel text unless it puts them back.
 */
public class SwingTest {

   public interface Job {
      void run() throws Exception;
   }

   // Run on the EDT and hand back whatever it threw, assertion failures included
   public static void run(Job job) throws Exception {
      final Throwable[] thrown = new Throwable[1];
      SwingUtilities.invokeAndWait(new Runnable() {
         @Override public void run() {
            try {
               job.run();
            } catch (Throwable t) {
               thrown[0] = t;
            }
         }
      });
      if (thrown[0] instanceof Error)
         throw (Error) thrown[0];
      if (thrown[0] instanceof Exception)
         throw (Exception) thrown[0];
   }

   // The usable area of the screen a window is on, the way Theme works it out
   public static Rectangle usableBounds(Window w) {
      GraphicsConfiguration gc = w.getGraphicsConfiguration();
      if (gc == null)
         return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
      Rectangle bounds = new Rectangle(gc.getBounds());
      Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
      bounds.x      += in.left;
      bounds.y      += in.top;
      bounds.width  -= in.left + in.right;
      bounds.height -= in.top  + in.bottom;
      return bounds;
   }

   // Everything Theme.apply and Theme.setFontSize write outside their own class
   public static class State {
      private final LookAndFeel look = UIManager.getLookAndFeel();
      private final Font defaultFont  = UIManager.getFont("defaultFont");
      private final Font tableFont    = UIManager.getFont("Table.font");
      private final Font headerFont   = UIManager.getFont("TableHeader.font");
      private final int fontSize      = config.FontSize;
      private final String theme      = config.lookAndFeel;

      public void restore() throws Exception {
         UIManager.put("defaultFont", defaultFont);
         UIManager.put("Table.font", tableFont);
         UIManager.put("TableHeader.font", headerFont);
         if (look != null)
            UIManager.setLookAndFeel(look);
         config.FontSize = fontSize;
         config.lookAndFeel = theme;
         config.gui = null;
      }
   }
}
