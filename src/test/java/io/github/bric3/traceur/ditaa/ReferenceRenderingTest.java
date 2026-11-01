/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReferenceRenderingTest {
  @TempDir Path directory;

  static Stream<Arguments> diagrams() throws Exception {
    String index;
    try (var input = ReferenceRenderingTest.class.getResourceAsStream("/compatibility/cases.tsv")) {
      assertNotNull(input, "Missing reference fixture index");
      index = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    return index
        .lines()
        .skip(1)
        .map(
            row -> {
              String[] fields = row.split("\t");
              return Arguments.of(
                  fields[0],
                  Integer.parseInt(fields[1]),
                  Double.parseDouble(fields[2]),
                  Boolean.parseBoolean(fields[3]),
                  Boolean.parseBoolean(fields[4]));
            });
  }

  @ParameterizedTest(name = "{0}, scale {2}, shadows {3}, separation {4}")
  @MethodSource("diagrams")
  void pngMatchesTheReferencePixels(
      String name, int variant, double scale, boolean shadows, boolean separation)
      throws Exception {
    String text;
    try (var input = getClass().getResourceAsStream("/compatibility/" + name + ".ditaa")) {
      assertNotNull(input);
      text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    var options = new RenderOptions(scale, shadows, separation);
    var output = new ByteArrayOutputStream();
    ImageSize size = new DitaaRenderer().render(text, options, OutputFormat.PNG, output);
    var actual = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));
    String runtime = Runtime.version().feature() == 11 ? "java11/" : "";
    try (var input =
        getClass()
            .getResourceAsStream("/compatibility/" + runtime + name + "-" + variant + ".png")) {
      assertNotNull(input);
      var expected = ImageIO.read(input);
      assertEquals(expected.getWidth(), size.width(), "Width");
      assertEquals(expected.getHeight(), size.height(), "Height");
      long differences = 0;
      for (int y = 0; y < size.height(); y++) {
        int[] expectedRow = expected.getRGB(0, y, size.width(), 1, null, 0, size.width());
        int[] actualRow = actual.getRGB(0, y, size.width(), 1, null, 0, size.width());
        if (!Arrays.equals(expectedRow, actualRow)) {
          for (int x = 0; x < size.width(); x++) if (expectedRow[x] != actualRow[x]) differences++;
        }
      }
      assertEquals(0, differences, "Different decoded RGBA pixels");
    }
  }

  @ParameterizedTest(name = "{0}, scale {2}, shadows {3}, separation {4}")
  @MethodSource("diagrams")
  void svgMatchesTheReferencePixels(
      String name, int variant, double scale, boolean shadows, boolean separation)
      throws Exception {
    String text;
    try (var input = getClass().getResourceAsStream("/compatibility/" + name + ".ditaa")) {
      assertNotNull(input);
      text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    var output = new ByteArrayOutputStream();
    new DitaaRenderer()
        .render(text, new RenderOptions(scale, shadows, separation), OutputFormat.SVG, output);
    var document = SvgEncodingTest.parse(output.toByteArray());
    assertEquals(0, document.getElementsByTagNameNS("*", "image").getLength());
    Path actualSvg = directory.resolve("actual.svg");
    Files.write(actualSvg, output.toByteArray());
    Path expectedSvg = directory.resolve("reference.svg");
    try (var input =
        getClass().getResourceAsStream("/compatibility/" + name + "-" + variant + ".svg")) {
      assertNotNull(input);
      Files.copy(input, expectedSvg);
    }
    Path expectedPng = rasterise(expectedSvg, "expected.png");
    Path actualPng = rasterise(actualSvg, "actual.png");
    var expected = ImageIO.read(expectedPng.toFile());
    var actual = ImageIO.read(actualPng.toFile());
    assertEquals(expected.getWidth(), actual.getWidth());
    assertEquals(expected.getHeight(), actual.getHeight());
    long differences = 0;
    for (int y = 0; y < expected.getHeight(); y++) {
      for (int x = 0; x < expected.getWidth(); x++)
        if (expected.getRGB(x, y) != actual.getRGB(x, y)) differences++;
    }
    assertEquals(
        0,
        differences,
        "Different SVG pixels after rasterising both vectors with the same renderer");
  }

  private Path rasterise(Path svg, String name) throws Exception {
    Path png = directory.resolve(name);
    Process process =
        new ProcessBuilder(
                System.getProperty("rasterizer", "rsvg-convert"),
                "--output",
                png.toString(),
                svg.toString())
            .redirectErrorStream(true)
            .redirectOutput(directory.resolve(name + ".log").toFile())
            .start();
    try {
      assertEquals(
          true,
          process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS),
          "SVG rasteriser timed out");
      assertEquals(0, process.exitValue(), "SVG rasteriser failed");
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor();
      }
    }
    return png;
  }
}
