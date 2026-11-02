/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.PathIterator;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConnectorTraversalTest {
  static Stream<Arguments> junctions() {
    return Stream.of(
        Arguments.of(
            "|\n+--\n|",
            List.of("25.0,49.0->45.0,49.0", "25.0,49.0->25.0,63.0", "25.0,35.0->25.0,49.0")),
        Arguments.of(
            "-+-\n |",
            List.of("35.0,35.0->35.0,49.0", "35.0,35.0->45.0,35.0", "25.0,35.0->35.0,35.0")),
        Arguments.of(
            " |\n-+-",
            List.of("35.0,49.0->25.0,49.0", "35.0,49.0->45.0,49.0", "35.0,35.0->35.0,49.0")),
        Arguments.of(
            " |\n-+\n |",
            List.of("35.0,49.0->25.0,49.0", "35.0,49.0->35.0,63.0", "35.0,35.0->35.0,49.0")),
        Arguments.of(
            " |\n-+-\n |",
            List.of(
                "35.0,49.0->25.0,49.0",
                "35.0,49.0->35.0,63.0",
                "35.0,49.0->45.0,49.0",
                "35.0,35.0->35.0,49.0")),
        Arguments.of(
            "|\n+-->\n|",
            List.of("25.0,49.0->55.0,49.0", "25.0,49.0->25.0,63.0", "25.0,35.0->25.0,49.0")));
  }

  @ParameterizedTest
  @MethodSource("junctions")
  void junctionsKeepTheReferenceStrokeOrderAndDirection(String text, List<String> expected) {
    var drawing = DiagramParser.parse(text, RenderOptions.DEFAULT);
    var paths =
        drawing.figures.stream()
            .filter(figure -> figure.kind == Drawing.Figure.Kind.CONNECTOR)
            .map(ConnectorTraversalTest::endpoints)
            .collect(Collectors.toList());
    assertEquals(expected, paths);
  }

  private static String endpoints(Drawing.Figure figure) {
    double[] coordinates = new double[6];
    String first = null, last = null;
    for (PathIterator path = figure.path.getPathIterator(null); !path.isDone(); path.next()) {
      int type = path.currentSegment(coordinates);
      assertTrue(type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO);
      last = coordinates[0] + "," + coordinates[1];
      if (first == null) first = last;
    }
    return first + "->" + last;
  }

  @Test
  void deeplyBranchedDiagramsRetainEveryPathWithoutUsingTheCallStack() throws Exception {
    int strips = 75, height = 300, width = (strips - 1) * 4 + 2;
    char[][] grid = new char[height][width];
    for (char[] row : grid) Arrays.fill(row, ' ');
    int junctions = 0;
    for (int strip = 0; strip < strips; strip++) {
      int x = strip * 4;
      for (int y = 0; y < height; y++) grid[y][x] = '|';
      for (int y = 2; y < height - 1; y += 2) {
        grid[y][x] = '+';
        grid[y][x + 1] = '-';
        junctions++;
      }
      if (strip > 0) {
        int y = strip % 2 == 1 ? height - 1 : 0;
        grid[y][x - 4] = grid[y][x] = '+';
        for (int dx = 1; dx < 4; dx++) grid[y][x - 4 + dx] = '-';
      }
    }
    String text = Arrays.stream(grid).map(String::new).collect(Collectors.joining("\n"));
    var output = new ByteArrayOutputStream();
    ImageSize size =
        new DitaaRenderer().render(text, RenderOptions.DEFAULT, OutputFormat.SVG, output);
    var document = SvgEncodingTest.parse(output.toByteArray());
    assertEquals(2 * junctions + 1, document.getElementsByTagNameNS("*", "path").getLength());
    assertEquals((width + 4) * 10, size.width());
    assertEquals((height + 4) * 14, size.height());
  }
}
