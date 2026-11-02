/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.font.FontRenderContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.tabletest.junit.TableTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;

class TextLayoutTest {
  @TableTest({
    "height | macSize | linuxSize",
    "2      | 1       | 1        ",
    "4      | 3       | 3        ",
    "7      | 6       | 6.5      ",
    "9      | 8       | 8.5      ",
    "12     | 12.5    | 12.5     ",
    "13     | 13      | 14       ",
    "14     | 14.5    | 15       ",
    "28     | 29      | 30       ",
    "42     | 43      | 45       "
  })
  void fontSizesMatchTheReferenceCellHeights(int height, float macSize, float linuxSize) {
    var drawing = new Drawing(100, 100, new RenderOptions((height + .25) / 14, false, true));
    assertEquals(height, drawing.cellHeight);
    assertEquals(
        System.getProperty("os.name").equals("Linux") ? linuxSize : macSize,
        drawing.font().getSize2D()
    );
  }

  @TableTest({
    "fontSize | baseline",
    "8        | 38      ",
    "9        | 38      ",
    "10       | 39      ",
    "11       | 39      ",
    "12       | 39      ",
    "13       | 40      ",
    "14       | 40      ",
    "14.5     | 40      ",
    "15       | 40      ",
    "28.5     | 45      "
  })
  void baselinesUseTheIntegerCapHeight(float fontSize, double baseline) {
    var label = new Drawing.Label(
        "Z",
        20,
        35,
        Color.BLACK,
        100,
        Drawing.FONT.deriveFont(fontSize),
        Drawing.TextAlignment.LEFT
    );
    assertEquals(baseline, label.y);
  }

  private static double width(Drawing.Label label) {
    return label.font
      .getStringBounds(label.text, new FontRenderContext(null, true, false))
      .getWidth();
  }

  @Test
  void diagonalTextRunsDoNotShareAlignment() {
    var drawing = DiagramParser.parse("   AB\n  /  \\\n /    \\\nBC", RenderOptions.DEFAULT);
    Drawing.Label label = drawing.labels.get(0);
    assertEquals("AB", label.text);
    assertEquals(60, label.x + width(label) / 2, .5);
  }

  @Test
  void threeRowsWithMatchingStartsAlignLeft() {
    var drawing = DiagramParser.parse("Text\nMore text\nThird", RenderOptions.DEFAULT);
    assertEquals(3, drawing.labels.size());
    for (var label : drawing.labels) {
      assertEquals(20, label.x);
    }
  }

  @Test
  void matchingRightEdgesAlignRightInTheEncodedSvg() throws Exception {
    String text = "    Short\nLong text";
    var drawing = DiagramParser.parse(text, RenderOptions.DEFAULT);
    var output = new ByteArrayOutputStream();
    new DitaaRenderer().render(text, RenderOptions.DEFAULT, OutputFormat.SVG, output);
    var labels = SvgEncodingTest.parse(output.toByteArray()).getElementsByTagNameNS("*", "text");
    assertEquals(2, labels.getLength());
    for (int i = 0; i < labels.getLength(); i++) {
      var label = drawing.labels.get(i);
      assertEquals(110, label.x + width(label));
      assertEquals(label.x, Double.parseDouble(((Element) labels.item(i)).getAttribute("x")));
    }
  }

  @Test
  void equalStartAndEndCountsKeepTextCentred() {
    var drawing = DiagramParser.parse("Text\nWord", RenderOptions.DEFAULT);
    for (var label : drawing.labels) {
      assertEquals(40, label.x + width(label) / 2, .5);
    }
  }

  @Test
  void blankRowsSeparateAlignmentBlocks() {
    var drawing = DiagramParser.parse("Text\nWord\n\nText\nMore text\nThird", RenderOptions.DEFAULT);
    assertEquals(5, drawing.labels.size());
    for (int i = 0; i < 2; i++) {
      assertEquals(40, drawing.labels.get(i).x + width(drawing.labels.get(i)) / 2, .5);
    }
    for (int i = 2; i < 5; i++) {
      assertEquals(20, drawing.labels.get(i).x);
    }
  }

  @Test
  void internalWordEndsContributeToAlignment() {
    var drawing = DiagramParser.parse("Text\nMore text", RenderOptions.DEFAULT);
    assertEquals(40, drawing.labels.get(0).x + width(drawing.labels.get(0)) / 2, .5);
    assertEquals(20, drawing.labels.get(1).x);
  }

  @Test
  void paddedSingleCharactersAlignWithoutReadingBeyondTheGrid() {
    var drawing = DiagramParser.parse("A\nB", RenderOptions.DEFAULT);
    assertEquals("A ", drawing.labels.get(0).text);
    assertEquals("B ", drawing.labels.get(1).text);
    assertEquals(20, drawing.labels.get(0).x);
    assertEquals(20, drawing.labels.get(1).x);
  }

  @ParameterizedTest
  @ValueSource(strings = {"WordZZ", "WordZZ more"})
  void paddingAfterASingleCharacterDoesNotAlignWithAnotherWordsEnd(String heading) {
    var drawing = DiagramParser.parse(heading + "\n    3", RenderOptions.DEFAULT);
    var label = drawing.labels.get(1);
    assertEquals("3 ", label.text);
    assertEquals(70, label.x + width(label) / 2, .5);
  }

  @TableTest({
    "layout | white",
    "0      | true ",
    "1      | false",
    "2      | true ",
    "3      | false"
  })
  void textContrastUsesLabelBoundsAgainstTheBoundaryPolygon(int layout, boolean white)
      throws IOException {
    var drawing = DiagramParser.parse(
        DiagramFixtures.layout("text-backgrounds", 4, layout),
        RenderOptions.DEFAULT
    );
    assertEquals(1, drawing.labels.size());
    assertEquals("X", drawing.labels.get(0).text.stripTrailing());
    assertEquals(white ? Color.WHITE : Color.BLACK, drawing.labels.get(0).colour);
  }

  @Test
  void filledRegionsSetTextContrastAfterLargerOpenFrames() throws IOException {
    var drawing =
        DiagramParser.parse(DiagramFixtures.text("foreground-order"), RenderOptions.DEFAULT);
    assertEquals(2, drawing.labels.size());
    for (var label : drawing.labels) {
      assertEquals(label.text.stripTrailing().equals("X") ? Color.WHITE : Color.BLACK, label.colour);
    }
  }
}
