package com.tivo.kmttg.gui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.gui.swing.SwingTest;
import com.tivo.kmttg.gui.swing.Theme;
import com.tivo.kmttg.main.config;

/**
 * How the configuration dialog copes with the GUI Font Size setting it owns.
 *
 * The dialog is built and packed once, so a font change used to leave it at
 * the size the old font needed - at 10 it packs to around 675x503, at 12 it
 * needs 811x626, and the last rows of settings and the OK button ended up
 * past the bottom edge. Beyond a certain font size the size it wants is more
 * than the screen has, which is what the tab scroll panes are for - the tests
 * for those shrink the dialog by hand rather than relying on the screen being
 * small enough to force the issue.
 *
 * Needs a display to build the dialog, so it stands aside when headless.
 */
public class ConfigDialogFitTest {

   private static SwingTest.State state;
   private static JDialog dialog;
   private static JTabbedPane tabs;
   private static JButton ok;

   @BeforeAll
   public static void buildDialog() throws Exception {
      assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
      state = new SwingTest.State();
      SwingTest.run(() -> {
         config.gui = new com.tivo.kmttg.gui.gui();
         setFont(10);
         invoke("create", JFrame.class, (JFrame) null);
         dialog = (JDialog) field("dialog");
         tabs   = (JTabbedPane) field("tabbed_panel");
         ok     = (JButton) field("OK");
      });
   }

   @AfterAll
   public static void release() throws Exception {
      if (state == null)
         return; // headless: the assumption stood the test aside before setup
      SwingTest.run(() -> {
         if (dialog != null)
            dialog.dispose();
         // Static, so leaving it set would hand a later display() a dead window
         Field f = configMain.class.getDeclaredField("dialog");
         f.setAccessible(true);
         f.set(null, null);
      });
      state.restore();
   }

   @BeforeEach
   public void startAtTheDefaultFont() throws Exception {
      SwingTest.run(() -> {
         setFont(10);
         fit();
      });
   }

   @Test
   public void aFontChangeResizesTheDialogToWhatTheSettingsNeed() throws Exception {
      SwingTest.run(() -> {
         int before = dialog.getHeight();
         setFont(14);
         fit();

         assertTrue(dialog.getHeight() > before,
            "dialog kept the height the smaller font needed, so the settings are cut off");
         assertTrue(dialog.getHeight() >= dialog.getContentPane().getPreferredSize().height
                 || dialog.getHeight() == usable().height,
            "dialog is neither tall enough for the settings nor capped at the screen");
      });
   }

   @Test
   public void aFontTooBigForTheScreenIsCappedAtIt() throws Exception {
      SwingTest.run(() -> {
         setFont(28);
         fit();

         Rectangle screen = usable();
         assertTrue(dialog.getWidth() <= screen.width && dialog.getHeight() <= screen.height,
            "dialog grew past the screen at " + dialog.getWidth() + "x" + dialog.getHeight());
      });
   }

   @Test
   public void theOkButtonStaysReachableHoweverShortTheDialogIs() throws Exception {
      SwingTest.run(() -> {
         setFont(28);
         fit();
         squeeze();

         int bottom = SwingUtilities.convertPoint(
            ok.getParent(), ok.getX(), ok.getY(), dialog).y + ok.getHeight();
         assertTrue(bottom <= dialog.getHeight(),
            "OK button sits " + (bottom - dialog.getHeight()) + " pixels below the dialog");
      });
   }

   @Test
   public void settingsTallerThanTheDialogScrollRatherThanVanish() throws Exception {
      SwingTest.run(() -> {
         setFont(28);
         fit();
         squeeze();

         JScrollPane tab = (JScrollPane) tabs.getComponentAt(tabs.getSelectedIndex());
         assertTrue(tab.getVerticalScrollBar().isVisible(),
            "no scrollbar, so the settings past the bottom cannot be reached");
      });
   }

   @Test
   public void anErroredSettingStillPointsAtTheTabItIsOn() throws Exception {
      SwingTest.run(() -> {
         Field f = configMain.class.getDeclaredField("FontSize");
         f.setAccessible(true);
         Method parent = configMain.class.getDeclaredMethod("getParentTab", Component.class);
         parent.setAccessible(true);

         int tab = (Integer) parent.invoke(null, f.get(null));
         assertTrue(tab != -1, "GUI Font Size no longer resolves to a tab, so errors cannot be flagged");
         assertEquals("Visual", tabs.getTitleAt(tab));
      });
   }

   // Shorter than the settings need, whatever the screen happens to be
   private static void squeeze() {
      dialog.setSize(dialog.getWidth(), 400);
      dialog.validate();
   }

   private static Rectangle usable() {
      return SwingTest.usableBounds(dialog);
   }

   private static void setFont(int size) {
      config.FontSize = size;
      Theme.apply("Light");
   }

   private static void fit() throws Exception {
      invoke("fit");
   }

   private static void invoke(String name, Object... args) throws Exception {
      Class<?>[] types = new Class<?>[args.length / 2];
      Object[] values = new Object[types.length];
      for (int i = 0; i < types.length; ++i) {
         types[i]  = (Class<?>) args[i * 2];
         values[i] = args[i * 2 + 1];
      }
      Method m = configMain.class.getDeclaredMethod(name, types);
      m.setAccessible(true);
      m.invoke(null, values);
   }

   private static Object field(String name) throws Exception {
      Field f = configMain.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(null);
   }
}
