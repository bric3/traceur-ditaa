/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectorStyleTest {
  private static List<Drawing.Figure> shafts(Drawing drawing) {
    return drawing.figures
      .stream()
      .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
      .collect(Collectors.toList());
  }

  @Test
  void aDashedCellStylesEveryArmOfTheOpenGroup() {
    var lines = shafts(DiagramParser.parse("===+-->\n   |\n   |", RenderOptions.DEFAULT));
    assertEquals(3, lines.size());
    assertTrue(lines.stream().allMatch(figure -> figure.dashed));
  }

  @Test
  void arrowsSeparateTheStylesOfTheirShafts() {
    var lines = shafts(DiagramParser.parse("==>\n  |\n  |", RenderOptions.DEFAULT));
    assertEquals(2, lines.size());
    assertTrue(lines
      .stream()
      .anyMatch(figure -> figure.dashed && figure.path.getBounds2D().getHeight() == 0)
    );
    assertTrue(lines
      .stream()
      .anyMatch(figure -> !figure.dashed && figure.path.getBounds2D().getWidth() == 0)
    );
  }

  @Test
  void pointMarkersJoinTheStylesOfTheirArms() {
    var drawing = DiagramParser.parse("==*-->\n  |\n  |", RenderOptions.DEFAULT);
    var lines = shafts(drawing);
    assertEquals(3, lines.size());
    assertTrue(lines.stream().allMatch(figure -> figure.dashed));
    assertFalse(
        drawing.figures
          .stream()
          .filter(figure -> figure.kind == Drawing.Figure.Kind.MARKER)
          .findFirst()
          .orElseThrow().dashed
    );
  }

  @Test
  void closedBoundariesSeparateStylesAndKeepShortDashedArrowsVisible() {
    var drawing = DiagramParser.parse(
        "+-----+\n|     +--->\n|     |\n+--+--+\n   :\n   v",
        RenderOptions.DEFAULT
    );
    var lines = shafts(drawing);
    assertEquals(1, lines.size());
    assertFalse(lines.get(0).dashed);
    assertEquals(
        2,
        drawing.figures
          .stream()
          .filter(figure -> figure.kind == Drawing.Figure.Kind.ARROW)
          .count()
    );
    assertTrue(drawing.labels.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"=>", "<=", "^\n:", ":\nv"})
  void anIsolatedDashedCellLeavesOnlyItsArrowhead(String text) {
    var drawing = DiagramParser.parse(text, RenderOptions.DEFAULT);
    assertEquals(1, drawing.figures.size());
    assertEquals(Drawing.Figure.Kind.ARROW, drawing.figures.get(0).kind);
    assertTrue(drawing.labels.isEmpty());
  }
}
