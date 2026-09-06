package com.tivo.kmttg.gui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

/**
 * Guards the wrapping labels in the Show information dialog.
 *
 * Every field the dialog shows except the time and the channel goes through
 * makeWrapLabel. The Swing port of the JavaFX layout pinned those labels with
 * setPreferredSize, reading the height off a label that was still empty - so
 * they were fixed at zero pixels tall and the title, subtitle, description,
 * ratings and cast were all laid out but never visible. The dialog looked like
 * the RPC had returned nothing when in fact it had returned everything.
 *
 * A height greater than zero once text is set is the whole invariant. The rest
 * pins the wrapping, the escaping, and the spacing rule around it - all of it
 * without opening a dialog, which needs a display.
 */
public class ShowDetailsLabelTest {

   private static final int WIDTH = 400;

   private static final String DESCRIPTION =
      "Siegfried is called to the Ministry of Agriculture, James and Helen settle into " +
      "married life, and Mrs Hall makes a discovery that changes everything for the " +
      "household at Skeldale House in the Yorkshire Dales.";

   @Test
   public void textGivesTheLabelHeight() {
      JLabel label = ShowDetails.makeWrapLabel(WIDTH);
      // The state the dialog is built in - nothing to show, no height needed
      assertEquals(0, label.getPreferredSize().height);

      ShowDetails.setWrapText(label, "All Creatures Great and Small on Masterpiece");
      assertTrue(label.getPreferredSize().height > 0,
         "a label with text is still zero pixels tall, so nothing renders");
   }

   @Test
   public void longTextWrapsToSeveralLinesAtTheGivenWidth() {
      JLabel one = ShowDetails.makeWrapLabel(WIDTH);
      ShowDetails.setWrapText(one, "Fixes");
      JLabel many = ShowDetails.makeWrapLabel(WIDTH);
      ShowDetails.setWrapText(many, DESCRIPTION);

      assertEquals(WIDTH, many.getPreferredSize().width, "wrapped at the wrong width");
      assertTrue(many.getPreferredSize().height > one.getPreferredSize().height * 2,
         "a long description did not wrap onto extra lines");
   }

   @Test
   public void emptyFieldsTakeNoRoom() {
      // A show with no subtitle must not leave a blank gap where one would be
      JLabel label = ShowDetails.makeWrapLabel(WIDTH);
      ShowDetails.setWrapText(label, "");
      assertEquals(0, label.getPreferredSize().height);
      ShowDetails.setWrapText(label, null);
      assertEquals(0, label.getPreferredSize().height);
   }

   @Test
   public void markupInShowTextIsEscapedNotRendered() {
      // Titles and descriptions are TiVo data - an unescaped '<' would be read
      // as a tag and swallow the rest of the line
      JLabel label = ShowDetails.makeWrapLabel(WIDTH);
      ShowDetails.setWrapText(label, "Tom & Jerry <not a tag>");
      assertTrue(label.getText().contains("Tom &amp; Jerry &lt;not a tag&gt;"), label.getText());
      assertTrue(label.getPreferredSize().height > 0);
   }

   @Test
   public void theGapUnderAFieldAddsToItsHeight() {
      JLabel bare = ShowDetails.makeWrapLabel(WIDTH);
      ShowDetails.setWrapText(bare, "Cheers");
      int unspaced = bare.getPreferredSize().height;

      JLabel spaced = ShowDetails.makeWrapLabel(WIDTH);
      ShowDetails.setWrapText(spaced, "Cheers");
      ShowDetails.applyFieldSpacing(new JLabel[][] {{spaced}, {bare}});

      assertTrue(spaced.getPreferredSize().height > unspaced,
         "the gap is in the border, so it has to show up in the height");
      // ...and the text still gets the full width to wrap in
      assertEquals(WIDTH, spaced.getPreferredSize().width);
   }

   @Test
   public void theBlockGapLandsOnTheLastFieldThatHasText() {
      // The case that made the title collide with the air time: a show with no
      // subtitle still needs the full gap under its title
      JLabel title = ShowDetails.makeWrapLabel(WIDTH);
      JLabel subtitle = ShowDetails.makeWrapLabel(WIDTH);
      JLabel next = ShowDetails.makeWrapLabel(WIDTH);
      ShowDetails.setWrapText(title, "Cheers");
      ShowDetails.setWrapText(subtitle, "");
      ShowDetails.setWrapText(next, "Sitcom; HD");

      ShowDetails.applyFieldSpacing(new JLabel[][] {{title, subtitle}, {next}});

      assertNotNull(title.getBorder(), "an absent subtitle left the title with no gap under it");
      assertNull(subtitle.getBorder(), "an empty field must not carry a gap");

      // With a subtitle present the gap moves down to it instead
      ShowDetails.setWrapText(subtitle, "\"Fixes\" (Sea 6 Ep 5)");
      ShowDetails.applyFieldSpacing(new JLabel[][] {{title, subtitle}, {next}});
      assertTrue(subtitle.getBorder().getBorderInsets(subtitle).bottom
               > title.getBorder().getBorderInsets(title).bottom,
         "the block gap should sit under the subtitle once there is one");
   }

   @Test
   public void theLastFieldWithTextCarriesNoTrailingGap() {
      // Otherwise it would double up with the window padding below it
      JLabel first = ShowDetails.makeWrapLabel(WIDTH);
      JLabel last = ShowDetails.makeWrapLabel(WIDTH);
      ShowDetails.setWrapText(first, "Sitcom; HD");
      ShowDetails.setWrapText(last, "Ted Danson, Shelley Long");

      ShowDetails.applyFieldSpacing(new JLabel[][] {{first}, {last}});
      assertNotNull(first.getBorder());
      assertNull(last.getBorder(), "the final field must not add to the window padding");
   }

   @Test
   public void anEmptyTrailingBlockDoesNotStrandAGap() {
      // A row with no rating and no cast ends at the description. Deciding the
      // trailing gap by block position rather than by what actually has text
      // left 10px hanging under it, on top of the window padding.
      JLabel description = ShowDetails.makeWrapLabel(WIDTH);
      JLabel otherInfo = ShowDetails.makeWrapLabel(WIDTH);
      JLabel actorInfo = ShowDetails.makeWrapLabel(WIDTH);
      ShowDetails.setWrapText(description, "Siegfried is called to the Ministry.");
      ShowDetails.setWrapText(otherInfo, "");
      ShowDetails.setWrapText(actorInfo, "");

      ShowDetails.applyFieldSpacing(
         new JLabel[][] {{description}, {otherInfo, actorInfo}});

      assertNull(description.getBorder(),
         "the last field with text kept a gap even though nothing follows it");
      assertNull(otherInfo.getBorder());
      assertNull(actorInfo.getBorder());
   }
}
