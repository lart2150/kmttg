package com.tivo.kmttg.gui.dialog;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.gui.swing.SwingTest;
import com.tivo.kmttg.gui.swing.Theme;
import com.tivo.kmttg.main.config;

/**
 * The two dialogs that hold more than a screen will take.
 *
 * Auto transfers wants 879x827 at font 12 and Advanced Search 992x797 at 14,
 * against the 720 pixels of height a 1366x768 display leaves usable - and
 * Windows caps a packed window at the monitor height regardless. Both used to
 * put their buttons in the same column as the settings, so the buttons were
 * the part that went off the bottom. A big screen would fit both outright, so
 * the scrolling is checked against a dialog shortened by hand rather than
 * against the display.
 *
 * Needs a display to build the dialogs, so it stands aside when headless.
 */
public class ScrollingDialogFitTest {

   private SwingTest.State state;
   private JDialog dialog;
   private Field staticField;

   @BeforeEach
   public void useALargeFont() {
      assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
      state = new SwingTest.State();
   }

   @AfterEach
   public void release() throws Exception {
      SwingTest.run(() -> {
         if (dialog != null)
            dialog.dispose();
         if (staticField != null)
            staticField.set(null, null);
      });
      state.restore();
   }

   @Test
   public void theAutoTransfersDialogFitsOnTheScreen() throws Exception {
      SwingTest.run(() -> {
         build(configAuto.class.getDeclaredConstructor().newInstance(), configAuto.class);
         // Static, so leaving it set would hand a later display() a dead window
         staticField = configAuto.class.getDeclaredField("dialog");
         staticField.setAccessible(true);
         assertFits("OK");
      });
   }

   @Test
   public void theAdvancedSearchDialogFitsOnTheScreen() throws Exception {
      SwingTest.run(() -> {
         build(new AdvSearch(), AdvSearch.class);
         dialog.setVisible(false); // create() shows it
         assertFits("Close");
      });
   }

   // Within the screen with its button reachable, and still reachable, over a
   // scroller, once the dialog is shorter than the settings it holds
   private void assertFits(String buttonText) {
      Rectangle screen = SwingTest.usableBounds(dialog);
      assertTrue(dialog.getWidth() <= screen.width && dialog.getHeight() <= screen.height,
         "dialog is " + dialog.getWidth() + "x" + dialog.getHeight()
            + ", past a screen of " + screen.width + "x" + screen.height);
      assertButtonInside(buttonText);

      dialog.setSize(dialog.getWidth(), 400);
      dialog.validate();
      assertButtonInside(buttonText);

      JScrollPane scroller = scroller(dialog.getContentPane());
      assertNotNull(scroller, "settings are not in a scroller, so they cannot be reached");
      assertTrue(scroller.getVerticalScrollBar().isVisible(),
         "no scrollbar, so the settings past the bottom cannot be reached");
   }

   private void assertButtonInside(String buttonText) {
      JButton button = button(dialog.getContentPane(), buttonText);
      assertNotNull(button, buttonText + " button is gone");
      int bottom = SwingUtilities.convertPoint(
         button.getParent(), button.getX(), button.getY(), dialog).y + button.getHeight();
      assertTrue(bottom <= dialog.getHeight(),
         buttonText + " sits " + (bottom - dialog.getHeight()) + " pixels below the dialog");
   }

   private void build(Object owner, Class<?> type) throws Exception {
      config.gui = new com.tivo.kmttg.gui.gui();
      config.FontSize = 14;
      Theme.apply("Light");

      Method create = type.getDeclaredMethod("create", JFrame.class);
      create.setAccessible(true);
      create.invoke(owner, (JFrame) null);
      Field f = type.getDeclaredField("dialog");
      f.setAccessible(true);
      dialog = (JDialog) f.get(owner);
      dialog.validate();
   }

   private JButton button(Container c, String text) {
      for (Component k : c.getComponents()) {
         if (k instanceof JButton && text.equals(((JButton) k).getText()))
            return (JButton) k;
         if (k instanceof Container) {
            JButton found = button((Container) k, text);
            if (found != null)
               return found;
         }
      }
      return null;
   }

   private JScrollPane scroller(Container c) {
      for (Component k : c.getComponents()) {
         if (k instanceof JScrollPane)
            return (JScrollPane) k;
         if (k instanceof Container) {
            JScrollPane found = scroller((Container) k);
            if (found != null)
               return found;
         }
      }
      return null;
   }
}
