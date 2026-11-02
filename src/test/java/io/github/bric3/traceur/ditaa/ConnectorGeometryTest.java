/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.tabletest.junit.TableTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectorGeometryTest {
  @TableTest({
    "Diagram | Horizontal | Start | End",
    "'/--\\n+--\\n|' | false | 35 | 63",
    "'|\\n+--\\n\\--' | false | 35 | 63",
    "'--\\\\n--+\\n  |' | false | 35 | 63",
    "'-+\\\\n ||\\n ||' | true | 25 | 45",
    "'/+-\\n||\\n||' | true | 25 | 45",
    "'/--\\n|\\n+--\\n|' | false | 63 | 77",
    "'/-----\\\\n+-----/\\n|' | false | 49 | 69"
  })
  void aStraightArmExtendsThroughTheNeighbouringOpenCorner(
      @ConvertWith(DiagramConverter.class) String text, boolean horizontal, double start, double end) {
    var drawing = DiagramParser.parse(text, RenderOptions.DEFAULT);
    var bounds =
        drawing.figures.stream()
            .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
            .map(figure -> figure.path.getBounds2D())
            .filter(box -> horizontal ? box.getHeight() == 0 : box.getWidth() == 0)
            .findFirst()
            .orElseThrow();
    assertEquals(start, horizontal ? bounds.getMinX() : bounds.getMinY());
    assertEquals(end, horizontal ? bounds.getMaxX() : bounds.getMaxY());
  }

  @TableTest({
    "text | left | right",
    "+- | 30 | 39",
    "-+ | 20 | 29",
    "+-+ | 30 | 39",
    "++- | 35 | 45",
    "-++ | 25 | 35",
    "+---- | 35 | 65"
  })
  void plusEndpointsLeaveTheReferenceLineExtent(String text, double left, double right) {
    var drawing = DiagramParser.parse(text, RenderOptions.DEFAULT);
    assertEquals(1, drawing.figures.size());
    assertTrue(drawing.labels.isEmpty());
    var bounds = drawing.figures.get(0).path.getBounds2D();
    assertEquals(left, bounds.getMinX());
    assertEquals(right, bounds.getMaxX());
  }

  @ParameterizedTest
  @ValueSource(strings = {"++", "+++", "+\n+\n+"})
  void adjacentPlusEndpointsAreConsumedTogether(String text) {
    var drawing = DiagramParser.parse(text, RenderOptions.DEFAULT);
    assertTrue(drawing.figures.isEmpty());
    assertTrue(drawing.labels.isEmpty());
  }

  @TableTest({
    "Diagram | Scale | Left | Top | Right | Bottom",
    "-> | 1 | 20 | 35 | 35 | 35",
    "<- | 1 | 25 | 35 | 39 | 35",
    "'^\\n|' | 1 | 25 | 35 | 25 | 55",
    "'|\\nv' | 1 | 25 | 28 | 25 | 49",
    "-> | .7 | 14 | 22 | 24 | 22",
    "<- | .7 | 17 | 22 | 27 | 22",
    "'^\\n|' | .7 | 17 | 22 | 17 | 35",
    "'|\\nv' | .7 | 17 | 18 | 17 | 31",
    "-> | 2 | 40 | 70 | 70 | 70",
    "<- | 2 | 50 | 70 | 79 | 70",
    "'^\\n|' | 2 | 50 | 70 | 50 | 111",
    "'|\\nv' | 2 | 50 | 56 | 50 | 98"
  })
  void aSingleShaftCellKeepsItsFreeCellEdge(
      @ConvertWith(DiagramConverter.class) String text, double scale, double left, double top, double right, double bottom) {
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
