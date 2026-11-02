/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RegionGeometryTest {
  static Stream<Arguments> inwardStubs() {
    List<Arguments> cases = new ArrayList<>();
    for (int side = 0; side < 4; side++)
      for (int length : new int[] {1, 2})
        for (boolean arrow : new boolean[] {false, true})
          for (int offset : new int[] {0, 3}) cases.add(Arguments.of(side, length, arrow, offset));
    return cases.stream();
  }

  @ParameterizedTest
  @MethodSource("inwardStubs")
  void anInwardStubAssignsTheFillToTheEnclosingFrame(
      int side, int length, boolean arrow, int offset) {
    var drawing = DiagramParser.parse(frame(side, length, arrow, offset), RenderOptions.DEFAULT);
    assertEquals(
        3,
        drawing.figures.stream()
            .filter(figure -> figure.kind == Drawing.Figure.Kind.REGION)
            .count());
    var green =
        drawing.figures.stream()
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
    var regions =
        drawing.figures.stream()
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
    var drawing =
        DiagramParser.parse(
            frame(0, 2, true, 0) + "\n\n+-----+\n| cBLU|\n+-----+", RenderOptions.DEFAULT);
    var blue =
        drawing.figures.stream()
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
    for (char[] row : cells) Arrays.fill(row, ' ');
    for (int y : new int[] {0, 2, 8, 10}) {
      Arrays.fill(cells[y], '-');
      cells[y][0] = cells[y][20] = '+';
    }
    for (int y : new int[] {1, 3, 4, 5, 6, 7, 9}) cells[y][0] = cells[y][20] = '|';

    int x = side == 0 || side == 2 ? 10 : side == 1 ? 20 : 0;
    int y = side == 0 ? 2 : side == 2 ? 8 : 5;
    int dx = side == 1 ? -1 : side == 3 ? 1 : 0;
    int dy = side == 0 ? 1 : side == 2 ? -1 : 0;
    cells[y][x] = '+';
    for (int i = 1; i <= length; i++) cells[y + i * dy][x + i * dx] = dx == 0 ? '|' : '-';
    if (arrow) cells[y + (length + 1) * dy][x + (length + 1) * dx] = "v<^>".charAt(side);
    System.arraycopy("cGRE".toCharArray(), 0, cells[6], 3, 4);
    return "\n".repeat(offset)
        + Arrays.stream(cells)
            .map(row -> " ".repeat(offset) + new String(row))
            .collect(Collectors.joining("\n"));
  }
}
