/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class DitaaRendererTest {
  private final DitaaRenderer renderer = new DitaaRenderer();
  private static final String BOX = "+----+\n| Hi |\n+----+";

  private byte[] render(String text, OutputFormat format) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    renderer.render(text, RenderOptions.DEFAULT, format, output);
    return output.toByteArray();
  }

  @Test
  void pngHasExpectedDimensionsAndVisibleContent() throws Exception {
    var image = ImageIO.read(new ByteArrayInputStream(render(BOX, OutputFormat.PNG)));
    assertEquals(100, image.getWidth());
    assertEquals(98, image.getHeight());
    assertEquals(0xff000000, image.getRGB(40, 35));
    assertEquals(0xffffffff, image.getRGB(30, 45));
  }

  @Test
  void svgEscapesTextWithoutEmbeddingRasterImages() throws Exception {
    byte[] svg = render("R&D \"quoted\" 'apostrophe'", OutputFormat.SVG);
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg));
    assertEquals(
        "R&D \"quoted\" 'apostrophe'",
        document.getElementsByTagName("text").item(0).getTextContent()
    );
    assertEquals(0, document.getElementsByTagName("image").getLength());
  }

  @Test
  void closedShapesAreFilledAndMarkupIsRemoved() throws Exception {
    var drawing =
        DiagramParser.parse("+-------+\n| cRED  |\n| {s} X |\n+-------+", RenderOptions.DEFAULT);
    assertTrue(drawing.figures
      .stream()
      .anyMatch(f -> new java.awt.Color(0xee3322).equals(f.fill)));
    assertEquals(List.of("X "), List.of(drawing.labels.get(0).text));
    assertTrue(new String(render(BOX, OutputFormat.SVG), java.nio.charset.StandardCharsets.UTF_8)
      .contains("<path")
    );
  }

  @Test
  void sharedAndNestedBoundariesProduceSeparateFilledRegions() {
    Drawing shared = DiagramParser.parse("+---+---+\n| A | B |\n+---+---+", RenderOptions.DEFAULT);
    assertEquals(2, shared.figures.stream().filter(f -> f.shadow).count());
    Drawing nested = DiagramParser.parse(
        "+-------+\n| +---+ |\n| | X | |\n| +---+ |\n+-------+",
        RenderOptions.DEFAULT
    );
    assertEquals(2, nested.figures.stream().filter(f -> f.shadow).count());
  }

  @Test
  void connectorInsideAClosedRegionRemainsVisible() throws Exception {
    String text = "+-----+\n|  |  |\n|  |  |\n+--+--+";
    var image = ImageIO.read(new ByteArrayInputStream(render(text, OutputFormat.PNG)));
    assertEquals(0xff000000, image.getRGB(55, 56));
    assertEquals(0xffffffff, image.getRGB(53, 56));
  }

  @Test
  void separationAndShadowsChangeOutput() throws Exception {
    String text = "+---+---+\n| A | B |\n+---+---+";
    var output = new ByteArrayOutputStream();
    renderer.render(text, new RenderOptions(1, false, false), OutputFormat.PNG, output);
    assertFalse(java.util.Arrays.equals(render(text, OutputFormat.PNG), output.toByteArray()));
  }

  @Test
  void dashedConnectorArrowsAndPointMarkersRender() {
    Drawing drawing = DiagramParser.parse("*===>\n|\nv", RenderOptions.DEFAULT);
    assertTrue(drawing.labels.isEmpty());
    assertTrue(drawing.figures.stream().anyMatch(f -> f.dashed));
    assertEquals(
        2,
        drawing.figures
          .stream()
          .filter(f -> java.awt.Color.BLACK.equals(f.fill))
          .count()
    );
  }

  @Test
  void lineEndingsAndTrailingNewlinesDoNotChangeOutput() throws Exception {
    assertArrayEquals(
        render(BOX, OutputFormat.PNG),
        render(BOX.replace("\n", "\r\n") + "\r\n", OutputFormat.PNG)
    );
    assertArrayEquals(
        render("\tHello", OutputFormat.SVG),
        render("        Hello", OutputFormat.SVG)
    );
  }

  @Test
  void unicodeAndEmptyInputsRender() throws Exception {
    assertTrue(render("café 日本語 😀", OutputFormat.PNG).length > 100);
    assertTrue(render("", OutputFormat.PNG).length > 50);
    assertThrows(IllegalArgumentException.class, () -> render("\uD800", OutputFormat.SVG));
    assertThrows(IllegalArgumentException.class, () -> render("\u0000", OutputFormat.SVG));
  }

  @Test
  void validatesScaleAndAllocationBeforeWriting() {
    for (double scale : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> new RenderOptions(scale, true, true));
    }
    var output = new ByteArrayOutputStream();
    assertThrows(IllegalArgumentException.class, () -> renderer.render(
        BOX,
        new RenderOptions(1e20, true, true),
        OutputFormat.PNG,
        output
    ));
    assertThrows(IllegalArgumentException.class, () -> render("x".repeat(250001), OutputFormat.PNG));
    assertEquals(0, output.size());
  }

  @Test
  void reportsScaledDimensions() throws Exception {
    var output = new ByteArrayOutputStream();
    var size = renderer.render(BOX, new RenderOptions(.5, true, true), OutputFormat.PNG, output);
    assertEquals(50, size.width());
    assertEquals(49, size.height());
  }

  @Test
  void streamOwnershipAndFailuresBelongToCaller() throws Exception {
    var stream = new ByteArrayOutputStream() {
      @Override
      public void close() {
        fail("Closed caller stream");
      }

      @Override
      public void flush() {
        fail("Flushed caller stream");
      }
    };
    renderer.render(BOX, RenderOptions.DEFAULT, OutputFormat.SVG, stream);
    OutputStream failing = new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        throw new IOException("disk full");
      }
    };
    assertThrows(IOException.class, () -> renderer.render(
        BOX,
        RenderOptions.DEFAULT,
        OutputFormat.PNG,
        failing
    ));
  }

  @Test
  void renderingIsConcurrentAndDeterministic() throws Exception {
    List<byte[]> expected = new ArrayList<>();
    var executor = Executors.newFixedThreadPool(4);
    try {
      List<Callable<byte[]>> tasks = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        String text = i % 2 == 0 ? BOX : "*===>\n|\nv";
        OutputFormat format = OutputFormat.values()[i % OutputFormat.values().length];
        RenderOptions options = new RenderOptions(i % 3 == 0 ? .7 : 1, i % 4 == 0, i % 3 == 0);
        var output = new ByteArrayOutputStream();
        renderer.render(text, options, format, output);
        expected.add(output.toByteArray());
        tasks.add(() -> {
          var concurrent = new ByteArrayOutputStream();
          renderer.render(text, options, format, concurrent);
          return concurrent.toByteArray();
        });
      }
      var results = executor.invokeAll(tasks);
      for (int i = 0; i < results.size(); i++) {
        assertArrayEquals(expected.get(i), results.get(i).get());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void svgNumbersAreLocaleIndependent() throws Exception {
    Locale before = Locale.getDefault();
    Locale display = Locale.getDefault(Locale.Category.DISPLAY);
    Locale format = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.FRANCE);
      var output = new ByteArrayOutputStream();
      renderer.render(BOX, new RenderOptions(.7, true, true), OutputFormat.SVG, output);
      var document = SvgEncodingTest.parse(output.toByteArray());
      var group = (org.w3c.dom.Element) document.getElementsByTagNameNS("*", "g").item(0);
      assertEquals(.7, Double.parseDouble(group.getAttribute("stroke-width")));
    } finally {
      Locale.setDefault(before);
      Locale.setDefault(Locale.Category.DISPLAY, display);
      Locale.setDefault(Locale.Category.FORMAT, format);
    }
  }
}
