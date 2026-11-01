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
    return DiagramParser.parse(text, RenderOptions.DEFAULT).labels.stream()
        .map(label -> label.text)
        .collect(Collectors.toList());
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
    assertEquals(List.of("Visual variable", "V v"), labels("Visual variable\nV v"));
    assertEquals(List.of("AB", "/ ", "BC"), labels("AB\n /\nBC"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"o", "*"})
  void bulletsMoveIntoTheFollowingSpaceAndConsecutiveRowsAlign(String marker) {
    var drawing =
        DiagramParser.parse(
            marker + " Text\n" + marker + " More text\n" + marker + " Third",
            RenderOptions.DEFAULT);
    assertEquals(
        List.of("\u2022Text", "\u2022More text", "\u2022Third"),
        drawing.labels.stream().map(label -> label.text).collect(Collectors.toList()));
    assertEquals(30, drawing.labels.get(0).x);
    assertEquals(30, drawing.labels.get(1).x);
    assertEquals(30, drawing.labels.get(2).x);
    assertTrue(drawing.figures.isEmpty());
    assertEquals(List.of("fooo bar", "o ", "bar", "o "), labels("fooo bar\no  bar\no"));
  }

  @Test
  void wideTextFitsItsCharacterCells() {
    var drawing = DiagramParser.parse("WWWWWW", RenderOptions.DEFAULT);
    Drawing.Label label = drawing.labels.get(0);
    assertTrue(label.font.getSize2D() < drawing.font.getSize2D());
    assertTrue(
        label
                .font
                .getStringBounds(label.text, new java.awt.font.FontRenderContext(null, true, false))
                .getWidth()
            <= 60);
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
    assertEquals(upper.figures.get(1).path.getBounds2D(), lower.figures.get(1).path.getBounds2D());
  }
}
