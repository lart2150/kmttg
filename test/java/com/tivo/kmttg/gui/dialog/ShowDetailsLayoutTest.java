package com.tivo.kmttg.gui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Where the pieces of the Show information window actually land.
 *
 * The label tests cover each field on its own; this one assembles the real
 * content pane, because the container layout is what decides whether the
 * artwork sits beside the title or floats half way down the text. FlowLayout
 * ignores alignmentY and centres its row, so the two setAlignmentY calls that
 * were meant to top align it did nothing at all - nothing caught it, because
 * nothing built the panel.
 *
 * Needs a display to construct the dialog, so it stands aside when headless.
 */
public class ShowDetailsLayoutTest {

   private ShowDetails details;
   private JPanel root;

   @BeforeEach
   public void buildPanel() throws Exception {
      assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
      details = new ShowDetails(null, null);
      root = details.buildContent();
   }

   @Test
   public void artworkStartsLevelWithTheTitle() throws Exception {
      // Text much taller than the picture, so centring and top aligning are
      // far apart and the assertion cannot pass by accident
      fill("Ghosts", "\"Halloween 5\" (Sea 5 Ep 11)",
         "Sun 08/09/26 03:00 PM (30 mins)", "11-1 WTTWDT",
         "Sam and Jay host a Halloween party for the ghosts, who each want the "
       + "night to go their own way, and the plans collide in ways nobody in the "
       + "house is ready for, least of all the living.",
         "Sitcom; HD; First Aired: 2026-01-04", "Rose McIver, Utkarsh Ambudkar");
      setIcon(new ImageIcon(new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB)));
      layout();

      JLabel title = field("mainTitle");
      JLabel image = field("image");
      assertTrue(image.getHeight() < title.getParent().getHeight() / 2,
         "picture is not shorter than the text, so this proves nothing");
      assertEquals(yInRoot(title), yInRoot(image),
         "artwork should start level with the title, not float beside the text");
   }

   @Test
   public void artworkSitsToTheRightOfTheTextWithAGap() throws Exception {
      fill("Ghosts", "", "Sun 08/09/26 03:00 PM", "11-1 WTTWDT", "", "", "");
      setIcon(new ImageIcon(new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB)));
      layout();

      JLabel image = field("image");
      JLabel title = field("mainTitle");
      int textRight = xInRoot(title) + title.getWidth();
      assertTrue(xInRoot(image) >= textRight,
         "artwork overlaps the text column");
      assertTrue(xInRoot(image) - textRight <= 20,
         "artwork drifted away from the text");
   }

   @Test
   public void withNoArtworkTheTextKeepsTheWholeWidth() throws Exception {
      fill("Ghosts", "", "Sun 08/09/26 03:00 PM", "11-1 WTTWDT", "", "", "");
      setIcon(null);
      layout();

      // No picture must not leave a strip of empty space beside the text
      Dimension size = root.getPreferredSize();
      JLabel title = field("mainTitle");
      assertEquals(size.width - 20, title.getWidth(),
         "something is taking width beside the text when there is no artwork");
   }

   private void fill(String... values) throws Exception {
      String[] names = {"mainTitle", "subTitle", "time", "channel",
                        "description", "otherInfo", "actorInfo"};
      for (int i = 0; i < names.length; ++i) {
         JLabel label = field(names[i]);
         if (names[i].equals("time") || names[i].equals("channel"))
            ShowDetails.setPlainText(label, values[i]);
         else
            ShowDetails.setWrapText(label, values[i]);
      }
      details.applyFieldSpacing();
   }

   private void layout() {
      root.setSize(root.getPreferredSize());
      root.addNotify();
      root.validate();
      root.doLayout();
   }

   private JLabel field(String name) throws Exception {
      Field f = ShowDetails.class.getDeclaredField(name);
      f.setAccessible(true);
      return (JLabel) f.get(details);
   }

   private void setIcon(ImageIcon icon) throws Exception {
      java.lang.reflect.Method m =
         ShowDetails.class.getDeclaredMethod("setShowImage", ImageIcon.class);
      m.setAccessible(true);
      m.invoke(details, icon);
   }

   private int yInRoot(JLabel label) {
      return SwingUtilities.convertPoint(label.getParent(), label.getX(), label.getY(), root).y;
   }

   private int xInRoot(JLabel label) {
      return SwingUtilities.convertPoint(label.getParent(), label.getX(), label.getY(), root).x;
   }
}
