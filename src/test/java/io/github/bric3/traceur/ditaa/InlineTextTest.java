/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.converter.ConvertWith;
import org.tabletest.junit.TableTest;

class InlineTextTest {
  @TableTest({
    "Text               ",
    "{A, 9, é, 漢, v, V}"
  })
  void aLetterOrDigitWithinAHorizontalLineKeepsItsLabel(String text) {
    var drawing = DiagramParser.parse("---" + text + "---", RenderOptions.DEFAULT);
    assertEquals(1, drawing.figures.size());
    var bounds = drawing.figures.get(0).path.getBounds2D();
    assertEquals(25, bounds.getMinX());
    assertEquals(85, bounds.getMaxX());
    assertEquals(0, bounds.getHeight());
    assertEquals(1, drawing.labels.size());
    assertEquals(text, drawing.labels.get(0).text.stripTrailing());
  }

  @TableTest({
    "Text         ",
    "{A, 9, é, 漢}"
  })
  void aLetterOrDigitWithinAVerticalLineKeepsItsLabel(String text) {
    var drawing = DiagramParser.parse("|\n" + text + "\n|", RenderOptions.DEFAULT);
    assertEquals(1, drawing.figures.size());
    var bounds = drawing.figures.get(0).path.getBounds2D();
    assertEquals(35, bounds.getMinY());
    assertEquals(63, bounds.getMaxY());
    assertEquals(0, bounds.getWidth());
    assertEquals(1, drawing.labels.size());
    assertEquals(text, drawing.labels.get(0).text.stripTrailing());
  }

  @TableTest({
    "Arrow ",
    "{v, V}"
  })
  void aDownwardArrowBetweenVerticalShaftCellsBecomesPartOfTheLine(String arrow) {
    var drawing = DiagramParser.parse("|\n" + arrow + "\n|", RenderOptions.DEFAULT);
    assertEquals(1, drawing.figures.size());
    assertEquals(Drawing.Figure.Kind.CONNECTOR, drawing.figures.get(0).kind);
    assertEquals(28, drawing.figures.get(0).path.getBounds2D().getHeight());
    assertTrue(drawing.labels.isEmpty());
  }

  @TableTest({
    "Arrows  ",
    "{vv, VV}"
  })
  void neighbouringDownwardArrowheadsKeepTheirOwnShafts(String arrows) {
    var drawing = DiagramParser.parse("||\n" + arrows, RenderOptions.DEFAULT);
    assertEquals(4, drawing.figures.size());
    assertEquals(
        2,
        drawing.figures
          .stream()
          .filter(figure -> figure.kind == Drawing.Figure.Kind.ARROW)
          .count()
    );
    assertTrue(drawing.labels.isEmpty());
  }

  @TableTest({
    "Text          ",
    "{_, /, !, ' '}"
  })
  void symbolsAndSpacesKeepTheGapBetweenHorizontalShafts(String text) {
    var drawing = DiagramParser.parse("--" + text + "--", RenderOptions.DEFAULT);
    assertEquals(2, drawing.figures.size());
    assertTrue(drawing.figures
      .stream()
      .allMatch(figure -> figure.path.getBounds2D().getWidth() == 10)
    );
  }

  @Test
  void aLetterAtACrossingKeepsItsTextAndAllFourArms() {
    var drawing = DiagramParser.parse(" |\n-A-\n |", RenderOptions.DEFAULT);
    assertEquals(4, drawing.figures.size());
    assertEquals(1, drawing.labels.size());
    assertEquals("A", drawing.labels.get(0).text.stripTrailing());
  }

  @Test
  void aWordBetweenHorizontalShaftsKeepsTheGap() {
    var drawing = DiagramParser.parse("--avb--", RenderOptions.DEFAULT);
    assertEquals(2, drawing.figures.size());
    assertEquals(1, drawing.labels.size());
    assertEquals("avb", drawing.labels.get(0).text);
  }

  @Test
  void aDownwardArrowWithinAWordRequiresAShaftAboveIt() {
    var drawing = DiagramParser.parse(" |\navb", RenderOptions.DEFAULT);
    assertEquals(2, drawing.figures.size());
    assertEquals(
        1,
        drawing.figures
          .stream()
          .filter(figure -> figure.kind == Drawing.Figure.Kind.ARROW)
          .count()
    );
    assertEquals(1, drawing.labels.size());
    assertEquals("a b", drawing.labels.get(0).text);
    assertEquals(Color.BLACK, drawing.labels.get(0).colour);
  }

  @Test
  void aDownwardArrowAboveItsOnlyShaftRemainsText() {
    var drawing = DiagramParser.parse("v\n|", RenderOptions.DEFAULT);
    assertEquals(1, drawing.figures.size());
    assertEquals(Drawing.Figure.Kind.CONNECTOR, drawing.figures.get(0).kind);
    assertEquals(1, drawing.labels.size());
    assertEquals("v", drawing.labels.get(0).text.stripTrailing());
  }

  @TableTest({
    "Diagram       ",
    "'^\\nText'    ",
    "'|\\nv\\nText'"
  })
  void anArrowheadSelectsWhiteTextWhenItsShaftDoesNotIntersectTheText(
      @ConvertWith(DiagramConverter.class) String text
  ) {
    var drawing = DiagramParser.parse(text, RenderOptions.DEFAULT);
    assertEquals(1, drawing.labels.size());
    assertEquals(Color.WHITE, drawing.labels.get(0).colour);
  }

  @Test
  void anIntersectingConnectorSelectsBlackTextInsideADarkRegion() {
    var drawing = DiagramParser.parse(
        "+------------+\n"
        + "|cBLK        |\n"
        + "|  |         |\n"
        + "| avb        |\n"
        + "|            |\n"
        + "+------------+",
        RenderOptions.DEFAULT
    );
    assertEquals(1, drawing.labels.size());
    assertEquals("a b", drawing.labels.get(0).text);
    assertEquals(Color.BLACK, drawing.labels.get(0).colour);
  }
}
