/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConnectorGeometryTest {
  static Stream<Arguments> shortArrows() {
    return Stream.of(
        Arguments.of("->", 1, 20, 35, 35, 35),
        Arguments.of("<-", 1, 25, 35, 39, 35),
        Arguments.of("^\n|", 1, 25, 35, 25, 55),
        Arguments.of("|\nv", 1, 25, 28, 25, 49),
        Arguments.of("->", .7, 14, 22, 24, 22),
        Arguments.of("<-", .7, 17, 22, 27, 22),
        Arguments.of("^\n|", .7, 17, 22, 17, 35),
        Arguments.of("|\nv", .7, 17, 18, 17, 31),
        Arguments.of("->", 2, 40, 70, 70, 70),
        Arguments.of("<-", 2, 50, 70, 79, 70),
        Arguments.of("^\n|", 2, 50, 70, 50, 111),
        Arguments.of("|\nv", 2, 50, 56, 50, 98));
  }

  @ParameterizedTest
  @MethodSource("shortArrows")
  void aSingleShaftCellKeepsItsFreeCellEdge(
      String text, double scale, double left, double top, double right, double bottom) {
    var drawing = DiagramParser.parse(text, new RenderOptions(scale, false, true));
    var shaft =
        drawing.figures.stream()
            .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
            .findFirst()
            .orElseThrow();
    var bounds = shaft.path.getBounds2D();
    assertEquals(left, bounds.getMinX());
    assertEquals(top, bounds.getMinY());
    assertEquals(right, bounds.getMaxX());
    assertEquals(bottom, bounds.getMaxY());
  }

  @Test
  void aSingleCellAttachedToAClosedBoundaryKeepsItsFreeEdge() {
    var drawing =
        DiagramParser.parse("+-----+\n|     |\n|     |\n+--+--+\n   |", RenderOptions.DEFAULT);
    var shaft =
        drawing.figures.stream()
            .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
            .findFirst()
            .orElseThrow();
    assertEquals(77, shaft.path.getBounds2D().getMinY());
    assertEquals(97, shaft.path.getBounds2D().getMaxY());
  }

  @Test
  void aBoundaryCrossingRetainsItsContinuousConnectorExtent() {
    var drawing =
        DiagramParser.parse("+-----+\n|  |  |\n|  |  |\n+--+--+\n   |", RenderOptions.DEFAULT);
    var shaft =
        drawing.figures.stream()
            .filter(
                figure ->
                    figure.kind == Drawing.Figure.Kind.CONNECTOR
                        && figure.path.getBounds2D().getMinY() == 77)
            .findFirst()
            .orElseThrow();
    assertEquals(91, shaft.path.getBounds2D().getMaxY());
  }

  @Test
  void pointMarkersLeaveTheShaftAtCellCentres() {
    var drawing = DiagramParser.parse("*-", RenderOptions.DEFAULT);
    var shaft =
        drawing.figures.stream()
            .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
            .findFirst()
            .orElseThrow();
    assertEquals(25, shaft.path.getBounds2D().getMinX());
    assertEquals(35, shaft.path.getBounds2D().getMaxX());
  }

  @Test
  void anArrowheadTerminatesEachAdjacentShaft() {
    var drawing = DiagramParser.parse(" |\n->-\n |", RenderOptions.DEFAULT);
    var shafts =
        drawing.figures.stream()
            .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
            .map(figure -> figure.path.getBounds2D())
            .collect(Collectors.toList());
    assertEquals(4, shafts.size());
    assertTrue(
        shafts.stream().anyMatch(bounds -> bounds.getMinX() == 20 && bounds.getMaxX() == 35));
    assertTrue(
        shafts.stream().anyMatch(bounds -> bounds.getMinX() == 35 && bounds.getMaxX() == 49));
    assertTrue(
        shafts.stream().anyMatch(bounds -> bounds.getMinY() == 28 && bounds.getMaxY() == 49));
    assertTrue(
        shafts.stream().anyMatch(bounds -> bounds.getMinY() == 49 && bounds.getMaxY() == 69));
  }

  @Test
  void arrowheadsDoNotConnectDirectlyToEachOtherOrToABoundary() {
    var drawing = DiagramParser.parse("->+---+\n  |   |\n  +---+\n\n>^", RenderOptions.DEFAULT);
    var shafts =
        drawing.figures.stream()
            .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
            .collect(Collectors.toList());
    assertEquals(1, shafts.size());
    assertEquals(20, shafts.get(0).path.getBounds2D().getMinX());
    assertEquals(35, shafts.get(0).path.getBounds2D().getMaxX());
  }
}
