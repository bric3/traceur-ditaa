/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextSyntaxTest {
  private static List<String> labels(String text) {
    return DiagramParser.parse(text, RenderOptions.DEFAULT).labels
      .stream()
      .map(label -> label.text)
      .collect(Collectors.toList());
  }

  @Test
  void fourTouchingRoundedBoxesKeepTheirCornerPairs() {
    String row = "/----\\/----\\\n|    ||    |\n\\----/\\----/\n";
    var drawing = DiagramParser.parse(row + row, RenderOptions.DEFAULT);
    assertEquals(4, drawing.figures.size());
    for (var figure : drawing.figures) {
      assertTrue(figure.shadow);
      assertEquals(50, figure.path.getBounds2D().getWidth());
      assertEquals(28, figure.path.getBounds2D().getHeight());
    }
  }

  @Test
  void touchingRoundedBoxesKeepSeparateInteriors() {
    var drawing =
        DiagramParser.parse("/----\\/----\\\n|    ||    |\n\\----/\\----/", RenderOptions.DEFAULT);
    assertEquals(2, drawing.figures.size());
    assertTrue(drawing.figures.stream().allMatch(figure -> figure.shadow));
    assertEquals(25, drawing.figures.get(0).path.getBounds2D().getMinX());
    assertEquals(85, drawing.figures.get(1).path.getBounds2D().getMinX());
  }

  @Test
  void markupOutsideShapesDisappearsWithoutSplittingText() {
    assertEquals(List.of("A B", "text"), labels("A*B    cRED {s} text"));
    assertEquals(List.of("A B A B A B"), labels("A-B A:B A|B"));
  }

  @Test
  void lowercaseHexadecimalTokensRemainText() {
    assertEquals(List.of("c0af", "cF0a", "cfff"), labels("c0af  cF0a  cfff"));
    assertTrue(labels("c0AF  cF0A  cFFF").isEmpty());
  }

  @Test
  void unconnectedStarsDisappearAndDirectionLettersRemainText() {
    assertTrue(labels("*  *").isEmpty());
    assertEquals(List.of("+ "), labels("+"));
    assertEquals(List.of("Visual variable", "V v"), labels("Visual variable\nV v"));
    assertEquals(List.of("AB", "/ ", "BC"), labels("AB\n /\nBC"));
  }

  @Test
  void bulletsRequireALetterOrDigitAfterTheSpace() {
    assertEquals(List.of("pointer"), labels("pointer * |"));
    assertEquals(List.of("[item]"), labels("* [item]"));
    assertEquals(List.of("!item"), labels("* !item"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"o", "*"})
  void bulletsMoveIntoTheFollowingSpaceAndConsecutiveRowsAlign(String marker) {
    var drawing = DiagramParser.parse(
        marker + " Text\n" + marker + " More text\n" + marker + " Third",
        RenderOptions.DEFAULT
    );
    assertEquals(
        List.of("•Text", "•More text", "•Third"),
        drawing.labels
          .stream()
          .map(label -> label.text)
          .collect(Collectors.toList())
    );
    assertEquals(30, drawing.labels.get(0).x);
    assertEquals(30, drawing.labels.get(1).x);
    assertEquals(30, drawing.labels.get(2).x);
    assertTrue(drawing.figures.isEmpty());
    assertEquals(List.of("fooo bar", "o ", "bar", "o "), labels("fooo bar\no  bar\no"));
    assertEquals(List.of("•élan"), labels(marker + " élan"));
    assertEquals(List.of("•١23"), labels(marker + " ١23"));
  }

  @Test
  void wideTextFitsItsCharacterCells() {
    var drawing = DiagramParser.parse("WWWWWW", RenderOptions.DEFAULT);
    Drawing.Label label = drawing.labels.get(0);
    assertTrue(label.font.getSize2D() < drawing.font().getSize2D());
    assertEquals(Math.floor(label.font.getSize2D()), label.font.getSize2D());
    assertTrue(
        label.font
          .getStringBounds(label.text, new java.awt.font.FontRenderContext(null, true, false))
          .getWidth() <= 60
    );
    assertEquals("WWWWWW", label.text);
  }

  @Test
  void longDashedConnectorsHaveOneContinuousPath() {
    var drawing = DiagramParser.parse("=".repeat(80), RenderOptions.DEFAULT);
    assertTrue(drawing.labels.isEmpty());
    assertEquals(1, drawing.figures.size());
    Drawing.Figure connector = drawing.figures.get(0);
    assertTrue(connector.dashed);
    assertNull(connector.fill);
    assertEquals(790, connector.path.getBounds2D().getWidth());
    assertEquals(0, connector.path.getBounds2D().getHeight());
  }

  @Test
  void connectedUppercaseAndLowercaseVerticalArrowsHaveTheSameGeometry() {
    var upper = DiagramParser.parse("|\nV", RenderOptions.DEFAULT);
    var lower = DiagramParser.parse("|\nv", RenderOptions.DEFAULT);
    assertEquals(2, upper.figures.size());
    assertTrue(upper.labels.isEmpty());
    var upperArrow = upper.figures
      .stream()
      .filter(figure -> figure.kind == Drawing.Figure.Kind.ARROW)
      .findFirst()
      .orElseThrow();
    var lowerArrow = lower.figures
      .stream()
      .filter(figure -> figure.kind == Drawing.Figure.Kind.ARROW)
      .findFirst()
      .orElseThrow();
    assertEquals(upperArrow.path.getBounds2D(), lowerArrow.path.getBounds2D());
  }
}
