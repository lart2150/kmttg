package com.tivo.kmttg.gui.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;

import javax.swing.JDialog;
import javax.swing.JLabel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What a font size change does to windows that are already on screen.
 *
 * Changing GUI Font Size from the Visual tab restyles every window but used to
 * leave each one at the size it was packed at. The dialogs are built once and
 * kept, so the clipping stuck: the Auto Transfers window packed at font 10
 * sits at 731x692 and needs 1037x949 at font 14. Windows that cannot be
 * dragged are packed again, and so are those still the size we gave them; once
 * the user drags one we only ever grow it, so their size survives.
 *
 * Needs a display to pack a dialog, so it stands aside when headless.
 */
public class ThemeRepackTest {

   private SwingTest.State state;
   private JDialog fixed;
   private JDialog resizable;

   @BeforeEach
   public void buildDialogs() throws Exception {
      assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
      state = new SwingTest.State();
      SwingTest.run(() -> {
         Theme.apply("Light");
         Theme.setFontSize(10);
         fixed = dialog(false);
         resizable = dialog(true);
      });
   }

   @AfterEach
   public void release() throws Exception {
      SwingTest.run(() -> {
         if (fixed != null) fixed.dispose();
         if (resizable != null) resizable.dispose();
      });
      state.restore();
   }

   @Test
   public void aBiggerFontGrowsTheDialogToFitIt() throws Exception {
      SwingTest.run(() -> {
         Dimension before = fixed.getSize();
         Theme.setFontSize(20);

         assertTrue(fixed.getHeight() > before.height,
            "dialog kept its old height, so the taller text is clipped");
         assertTrue(fixed.getHeight() >= fixed.getContentPane().getPreferredSize().height,
            "dialog is shorter than the content it now has to show");
      });
   }

   @Test
   public void aSmallerFontGivesTheSpaceBack() throws Exception {
      SwingTest.run(() -> {
         Theme.setFontSize(20);
         Dimension large = fixed.getSize();
         Theme.setFontSize(10);

         assertTrue(fixed.getHeight() < large.height,
            "dialog stayed at the size the larger font needed");
      });
   }

   @Test
   public void aWindowStillTheSizeWeGaveItIsFittedBothWays() throws Exception {
      SwingTest.run(() -> {
         Theme.fitToScreen(resizable);
         Dimension fitted = resizable.getSize();
         Theme.setFontSize(20);
         assertTrue(resizable.getWidth() > fitted.width, "did not grow with the font");
         Theme.setFontSize(10);
         assertEquals(fitted, resizable.getSize(),
            "did not take the space back when the font shrank");
      });
   }

   @Test
   public void aWindowTheUserSizedIsNeverShrunk() throws Exception {
      SwingTest.run(() -> {
         resizable.setSize(400, 300);
         Theme.setFontSize(20);
         Theme.setFontSize(10);

         assertEquals(new Dimension(400, 300), resizable.getSize(),
            "resized a window the user had sized themselves");
      });
   }

   @Test
   public void aWindowTooSmallForItsContentsIsGrown() throws Exception {
      SwingTest.run(() -> {
         resizable.setSize(50, 20);
         Theme.setFontSize(20);

         Dimension needed = resizable.getPreferredSize();
         assertTrue(resizable.getWidth() >= needed.width
                 && resizable.getHeight() >= needed.height,
            "dialog stayed too small for what it has to show");
      });
   }

   private JDialog dialog(boolean canResize) {
      JDialog d = new JDialog((JDialog)null);
      d.getContentPane().add(new JLabel("kmttg configuration"));
      d.setResizable(canResize);
      d.pack();
      return d;
   }
}
