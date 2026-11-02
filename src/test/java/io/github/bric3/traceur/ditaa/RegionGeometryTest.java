/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.io.IOException;
import static io.github.bric3.traceur.ditaa.DiagramFixtures.layout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.tabletest.junit.TableTest;
import org.junit.jupiter.params.provider.MethodSource;

class RegionGeometryTest {
  @Test
  void separateBranchedFramesKeepOnlyTheirConnectingFill() throws IOException {
    assertRegionBounds(
        DiagramParser.parse(layout("branched-pair", 2, 1), RenderOptions.DEFAULT),
        1,
        50,
        14
    );
  }

  @TableTest({
    "layout | regions | width | height",
    "0      | 3       | 350   | 84    ",
    "1      | 2       | 70    | 28    ",
    "2      | 2       | 70    | 28    "
  })
  void connectedInnerCyclesRetainIndependentFills(int index, int count, double width, double height)
      throws IOException {
    assertRegionBounds(
        DiagramParser.parse(layout("nested-split-cycles", 3, index), RenderOptions.DEFAULT),
        count,
        width,
        height
    );
  }

  @TableTest({
    "layout | regions | width | height",
    "0      | 2       | 238   | 38    ",
    "1      | 2       | 76    | 166   ",
    "2      | 2       | 238   | 38    ",
    "3      | 2       | 238   | 38    ",
    "4      | 2       | 238   | 54    ",
    "5      | 1       | 80    | 14    ",
    "6      | 2       | 38    | 42    "
  })
  void sharedBranchedCompartmentsKeepTheirEnclosingFills(
      int index,
      int count,
      double width,
      double height
  ) throws IOException {
    var drawing =
        DiagramParser.parse(layout("shared-open-compartments", 7, index), RenderOptions.DEFAULT);
    assertRegionBounds(drawing, count, width, height);
  }

  @ParameterizedTest
  @MethodSource("isolatedBranches")
  void isolatedBranchesAndLooseLinesKeepTheReferenceFills(
      String text,
      int count,
      double width,
      double height
  ) {
    assertRegionBounds(DiagramParser.parse(text, RenderOptions.DEFAULT), count, width, height);
  }

  static Stream<Arguments> isolatedBranches() {
    return Stream.of(
        Arguments.of("+-------+-------+\n|   |   +-------+\n|   |   |\n+---+---+", 1, 80, 14),
        Arguments.of(
            "+-------+-------+\n|       |       |\n|   |   |       |\n+---+---+-------+-",
            1,
            80,
            42
        ),
        Arguments.of(
            "+-------+-------+\n|       +--+    |\n|   |      |    |\n+---+------+----+-",
            1,
            80,
            42
        ),
        Arguments.of("+--+ +--+\n|  | |  |\n+--+ +--+\n\n|", 2, 30, 28)
    );
  }

  private static void assertRegionBounds(Drawing drawing, int count, double width, double height) {
    var regions = drawing.figures
      .stream()
      .filter(figure -> figure.kind == Drawing.Figure.Kind.REGION)
      .collect(Collectors.toList());
    assertEquals(count, regions.size());
    var bounds = regions
      .stream()
      .max(Comparator.comparingDouble(figure -> figure.area))
      .orElseThrow().path.getBounds2D();
    assertEquals(width, bounds.getWidth());
    assertEquals(height, bounds.getHeight());
    assertTrue(drawing.labels.isEmpty());
  }

  @Test
  void scaledWideDiagramsRetainDistantRegions() {
    String gap = " ".repeat(21996);
    String text = "+--+" + gap + "+--+\n|  |" + gap + "|  |\n+--+" + gap + "+--+";
    var drawing = DiagramParser.parse(text, new RenderOptions(.1, false, true));
    assertEquals(2, drawing.figures.size());
    assertTrue(drawing.figures
      .stream()
      .allMatch(figure -> figure.kind == Drawing.Figure.Kind.REGION)
    );
    assertEquals(
        List.of(2.0, 22002.0),
        drawing.figures
          .stream()
          .map(figure -> figure.path.getBounds2D().getMinX())
          .sorted()
          .collect(Collectors.toList())
    );
  }

  @TableTest({
    "Compartments",
    "{2, 3, 4}   "
  })
  void oneCellHighCompartmentsKeepOnlyTheirOwnFills(int compartments) {
    String row = "+".repeat(compartments + 1);
    var drawing = DiagramParser.parse(row + "\n" + row, new RenderOptions(1, false, true));
    assertEquals(compartments, drawing.figures.size());
    assertTrue(drawing.labels.isEmpty());
    for (var figure : drawing.figures) {
      assertEquals(Drawing.Figure.Kind.REGION, figure.kind);
      assertEquals(35, figure.path.getBounds2D().getMinY());
      assertEquals(49, figure.path.getBounds2D().getMaxY());
    }
  }

  @TableTest({
    "layout | arrows",
    "0      | 2     ",
    "1      | 1     ",
    "2      | 1     "
  })
  void arrowheadsCannotCloseARegion(int layout, int arrows) throws IOException {
    var drawing = DiagramParser.parse(layout("arrow-regions", 5, layout), RenderOptions.DEFAULT);
    assertEquals(
        arrows,
        drawing.figures
          .stream()
          .filter(figure -> figure.kind == Drawing.Figure.Kind.ARROW)
          .count()
    );
    assertTrue(drawing.figures
      .stream()
      .noneMatch(figure -> figure.kind == Drawing.Figure.Kind.REGION)
    );
    assertTrue(drawing.figures
      .stream()
      .noneMatch(figure -> figure.shadow));
    assertTrue(drawing.labels.isEmpty());
  }

  @Test
  void anOutgoingArrowKeepsTheFrameClosed() throws IOException {
    var drawing = DiagramParser.parse(layout("arrow-regions", 5, 3), RenderOptions.DEFAULT);
    assertEquals(
        1,
        drawing.figures
          .stream()
          .filter(figure -> figure.kind == Drawing.Figure.Kind.REGION)
          .count()
    );
    assertEquals(
        1,
        drawing.figures
          .stream()
          .filter(figure -> figure.kind == Drawing.Figure.Kind.ARROW)
          .count()
    );
  }

  @Test
  void aVerticalInlineLetterKeepsTheFrameClosed() throws IOException {
    var drawing = DiagramParser.parse(layout("arrow-regions", 5, 4), RenderOptions.DEFAULT);
    assertEquals(1, drawing.figures.size());
    assertEquals(Drawing.Figure.Kind.REGION, drawing.figures.get(0).kind);
    assertTrue(drawing.figures.get(0).shadow);
    assertTrue(drawing.labels.isEmpty());
  }

  @TableTest({
    "layout | expected",
    "0      | 2       ",
    "1      | 2       ",
    "3      | 3       ",
    "5      | 5       "
  })
  void adjacentClosedCornersRetainTheirRegions(int layout, int expected) throws IOException {
    var drawing =
        DiagramParser.parse(layout("corner-connections", 6, layout), RenderOptions.DEFAULT);
    assertEquals(expected, drawing.figures.size());
    assertTrue(drawing.figures
      .stream()
      .allMatch(figure -> figure.kind == Drawing.Figure.Kind.REGION)
    );
    assertTrue(drawing.labels.isEmpty());
  }

  @Test
  void anExplicitShaftConnectsAdjacentRegions() throws IOException {
    var drawing = DiagramParser.parse(layout("corner-connections", 6, 2), RenderOptions.DEFAULT);
    assertEquals(3, drawing.figures.size());
    var connector = drawing.figures
      .stream()
      .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
      .findFirst()
      .orElseThrow();
    var bounds = connector.path.getBounds2D();
    assertEquals(65, bounds.getMinX());
    assertEquals(85, bounds.getMaxX());
    assertEquals(35, bounds.getMinY());
    assertEquals(0, bounds.getHeight());
  }

  @Test
  void aShortShaftKeepsItsLengthBesideAdjacentCorners() throws IOException {
    var drawing = DiagramParser.parse(layout("corner-connections", 6, 4), RenderOptions.DEFAULT);
    assertEquals(3, drawing.figures.size());
    var connector = drawing.figures
      .stream()
      .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
      .findFirst()
      .orElseThrow();
    var bounds = connector.path.getBounds2D();
    assertEquals(20, bounds.getMinX());
    assertEquals(35, bounds.getMaxX());
    assertEquals(63, bounds.getMinY());
    assertEquals(0, bounds.getHeight());
  }

  @Test
  void nestedStacksKeepTheirEnclosingContour() throws IOException {
    var drawing = DiagramParser.parse(nestedLayout(0), RenderOptions.DEFAULT);
    assertEquals(4, drawing.figures.size());
    var bounds = drawing.figures
      .stream()
      .filter(figure -> Color.WHITE.equals(figure.fill))
      .map(figure -> figure.path.getBounds2D())
      .filter(box -> box.getHeight() == 52)
      .findFirst()
      .orElseThrow();
    assertEquals(47, bounds.getMinX());
    assertEquals(65, bounds.getMinY());
    assertEquals(133, bounds.getMaxX());
    assertEquals(117, bounds.getMaxY());
  }

  @Test
  void nestedNeighboursKeepTheirEnclosingContour() throws IOException {
    var drawing = DiagramParser.parse(nestedLayout(1), RenderOptions.DEFAULT);
    assertEquals(4, drawing.figures.size());
    var bounds = drawing.figures
      .stream()
      .filter(figure -> Color.WHITE.equals(figure.fill))
      .map(figure -> figure.path.getBounds2D())
      .filter(box -> box.getWidth() == 86)
      .findFirst()
      .orElseThrow();
    assertEquals(47, bounds.getMinX());
    assertEquals(65, bounds.getMinY());
    assertEquals(133, bounds.getMaxX());
    assertEquals(89, bounds.getMaxY());
  }

  @Test
  void regionsSharingOneCornerKeepSeparateContours() throws IOException {
    var drawing = DiagramParser.parse(nestedLayout(2), RenderOptions.DEFAULT);
    assertEquals(3, drawing.figures.size());
    var boxes = drawing.figures
      .stream()
      .filter(figure -> Color.WHITE.equals(figure.fill))
      .map(figure -> figure.path.getBounds2D())
      .collect(Collectors.toList());
    assertEquals(2, boxes.size());
    assertTrue(boxes
      .stream()
      .allMatch(box -> box.getWidth() == 40 && box.getHeight() == 28));
    assertTrue(boxes
      .stream()
      .anyMatch(box -> box.getMinX() == 45 && box.getMinY() == 63));
    assertTrue(boxes
      .stream()
      .anyMatch(box -> box.getMinX() == 85 && box.getMinY() == 91));
  }

  @Test
  void aConcaveParentMustContainTheInnerContour() throws IOException {
    var drawing = DiagramParser.parse(nestedLayout(3), RenderOptions.DEFAULT);
    assertEquals(3, drawing.figures.size());
    var boxes = drawing.figures
      .stream()
      .filter(figure -> Color.WHITE.equals(figure.fill))
      .map(figure -> figure.path.getBounds2D())
      .collect(Collectors.toList());
    assertEquals(2, boxes.size());
    assertTrue(boxes
      .stream()
      .allMatch(box -> box.getMinX() == 115 && box.getWidth() == 40));
  }

  @Test
  void anUnnestedStackKeepsItsOriginalOutsideEdges() {
    var drawing = DiagramParser.parse(
        "+-------+\n|       |\n+-------+\n|       |\n+-------+",
        RenderOptions.DEFAULT
    );
    assertEquals(2, drawing.figures.size());
    assertTrue(drawing.figures
      .stream()
      .allMatch(figure -> figure.path.getBounds2D().getMinX() == 25
          && figure.path.getBounds2D().getMaxX() == 105)
    );
  }

  private static String nestedLayout(int index) throws IOException {
    return layout("nested-subdivisions", 4, index);
  }

  static Stream<Arguments> inwardStubs() {
    List<Arguments> cases = new ArrayList<>();
    for (int side = 0; side < 4; side++) {
      for (int length : new int[] {1, 2}) {
        for (boolean arrow : new boolean[] {false, true}) {
          for (int offset : new int[] {0, 3}) {
            cases.add(Arguments.of(side, length, arrow, offset));
          }
        }
      }
    }
    return cases.stream();
  }

  @ParameterizedTest
  @MethodSource("inwardStubs")
  void anInwardStubAssignsTheFillToTheEnclosingFrame(
      int side,
      int length,
      boolean arrow,
      int offset
  ) {
    var drawing = DiagramParser.parse(frame(side, length, arrow, offset), RenderOptions.DEFAULT);
    assertEquals(
        3,
        drawing.figures
          .stream()
          .filter(figure -> figure.kind == Drawing.Figure.Kind.REGION)
          .count()
    );
    var green = drawing.figures
      .stream()
      .filter(figure -> new Color(0x99dd99).equals(figure.fill))
      .findFirst()
      .orElseThrow();
    var bounds = green.path.getBounds2D();
    assertEquals(27 + offset * 10, bounds.getMinX());
    assertEquals(37 + offset * 14, bounds.getMinY());
    assertEquals(223 + offset * 10, bounds.getMaxX());
    assertEquals(173 + offset * 14, bounds.getMaxY());
    assertTrue(drawing.labels.isEmpty());
  }

  @Test
  void aCrossingRetainsTheClosedFrame() {
    var drawing =
        DiagramParser.parse("+-----+\n|  |  |\n|  |  |\n+--+--+\n   |", RenderOptions.DEFAULT);
    var regions = drawing.figures
      .stream()
      .filter(figure -> figure.kind == Drawing.Figure.Kind.REGION)
      .collect(Collectors.toList());
    assertEquals(1, regions.size());
    var bounds = regions.get(0).path.getBounds2D();
    assertEquals(25, bounds.getMinX());
    assertEquals(35, bounds.getMinY());
    assertEquals(85, bounds.getMaxX());
    assertEquals(77, bounds.getMaxY());
  }

  @Test
  void separateFramesKeepTheirOwnFill() {
    var drawing = DiagramParser.parse(
        frame(0, 2, true, 0) + "\n\n+-----+\n| cBLU|\n+-----+",
        RenderOptions.DEFAULT
    );
    var blue = drawing.figures
      .stream()
      .filter(figure -> new Color(0x5555bb).equals(figure.fill))
      .findFirst()
      .orElseThrow();
    var bounds = blue.path.getBounds2D();
    assertEquals(25, bounds.getMinX());
    assertEquals(203, bounds.getMinY());
    assertEquals(85, bounds.getMaxX());
    assertEquals(231, bounds.getMaxY());
  }

  private static String frame(int side, int length, boolean arrow, int offset) {
    char[][] cells = new char[11][21];
    for (char[] row : cells) {
      Arrays.fill(row, ' ');
    }
    for (int y : new int[] {0, 2, 8, 10}) {
      Arrays.fill(cells[y], '-');
      cells[y][0] = cells[y][20] = '+';
    }
    for (int y : new int[] {1, 3, 4, 5, 6, 7, 9}) {
      cells[y][0] = cells[y][20] = '|';
    }

    int x = side == 0 || side == 2 ? 10 : side == 1 ? 20 : 0;
    int y = side == 0 ? 2 : side == 2 ? 8 : 5;
    int dx = side == 1 ? -1 : side == 3 ? 1 : 0;
    int dy = side == 0 ? 1 : side == 2 ? -1 : 0;
    cells[y][x] = '+';
    for (int i = 1; i <= length; i++) {
      cells[y + i * dy][x + i * dx] = dx == 0 ? '|' : '-';
    }
    if (arrow) {
      cells[y + (length + 1) * dy][x + (length + 1) * dx] = "v<^>".charAt(side);
    }
    System.arraycopy("cGRE".toCharArray(), 0, cells[6], 3, 4);
    return "\n".repeat(offset)
        + Arrays
          .stream(cells)
          .map(row -> " ".repeat(offset) + new String(row))
          .collect(Collectors.joining("\n"));
  }
}
