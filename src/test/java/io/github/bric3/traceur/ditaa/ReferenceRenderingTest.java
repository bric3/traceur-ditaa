/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReferenceRenderingTest {
  private static final String REFERENCE_DIRECTORY =
      System.getProperty("os.name").equals("Linux") ? "linux/" : "";

  static Stream<Arguments> diagrams() throws Exception {
    return new String(DiagramFixtures.read("cases.tsv"), StandardCharsets.UTF_8)
        .lines()
        .skip(1)
        .map(row -> {
          String[] fields = row.split("\t");
          return Arguments.of(fields[0], Integer.parseInt(fields[1]), Double.parseDouble(fields[2]),
              Boolean.parseBoolean(fields[3]), Boolean.parseBoolean(fields[4]));
        });
  }

  @ParameterizedTest(name = "{0}, scale {2}, shadows {3}, separation {4}")
  @MethodSource("diagrams")
  void pngMatchesTheReferencePixels(
      String name, int variant, double scale, boolean shadows, boolean separation) throws Exception {
    var output = new ByteArrayOutputStream();
    ImageSize size = new DitaaRenderer().render(DiagramFixtures.text(name),
        new RenderOptions(scale, shadows, separation), OutputFormat.PNG, output);
    var actual = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));
    String runtime = Runtime.version().feature() == 11 ? "java11/" : "";
    var expected = ImageIO.read(new ByteArrayInputStream(
        DiagramFixtures.read(REFERENCE_DIRECTORY + runtime + name + "-" + variant + ".png")));
    assertNotNull(expected);
    assertEquals(expected.getWidth(), size.width(), "Width");
    assertEquals(expected.getHeight(), size.height(), "Height");
    assertPixels(expected, actual);
  }

  @ParameterizedTest(name = "{0}, scale {2}, shadows {3}, separation {4}")
  @MethodSource("diagrams")
  void svgMatchesTheReferencePixels(
      String name, int variant, double scale, boolean shadows, boolean separation) throws Exception {
    var output = new ByteArrayOutputStream();
    new DitaaRenderer().render(DiagramFixtures.text(name), new RenderOptions(scale, shadows, separation),
        OutputFormat.SVG, output);
    byte[] svg = output.toByteArray();
    var document = SvgEncodingTest.parse(svg);
    assertEquals(0, document.getElementsByTagNameNS("*", "image").getLength());
    assertPixels(rasterise(DiagramFixtures.read(REFERENCE_DIRECTORY + name + "-" + variant + ".svg")), rasterise(svg));
  }

  private static BufferedImage rasterise(byte[] svg) {
    var document = new SVGLoader().load(new ByteArrayInputStream(svg), null, LoaderContext.createDefault());
    assertNotNull(document, "Invalid SVG");
    var image = new BufferedImage((int) Math.ceil(document.size().width),
        (int) Math.ceil(document.size().height), BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      document.render(null, graphics);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static void assertPixels(BufferedImage expected, BufferedImage actual) {
    assertNotNull(actual, "Missing rendered image");
    int width = expected.getWidth();
    assertEquals(width, actual.getWidth(), "Width");
    assertEquals(expected.getHeight(), actual.getHeight(), "Height");
    int[] expectedRow = new int[width], actualRow = new int[width];
    long differences = 0;
    for (int y = 0; y < expected.getHeight(); y++) {
      expected.getRGB(0, y, width, 1, expectedRow, 0, width);
      actual.getRGB(0, y, width, 1, actualRow, 0, width);
      for (int x = 0; x < width; x++) if (expectedRow[x] != actualRow[x]) differences++;
    }
    assertEquals(0, differences, "Different decoded RGBA pixels");
  }
}
