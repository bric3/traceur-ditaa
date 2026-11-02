/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageWriterSpi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RendererValidationTest {
  private final DitaaRenderer renderer = new DitaaRenderer();

  @Test
  void aBlankGridAtTheCellLimitRendersWithoutLabelsOrPaths() throws Exception {
    var output = new ByteArrayOutputStream();
    ImageSize size = renderer.render(
        (" ".repeat(500) + "\n").repeat(500),
        new RenderOptions(.2, false, true),
        OutputFormat.SVG,
        output
    );
    assertEquals(1008, size.width());
    assertEquals(1008, size.height());
    var document = SvgEncodingTest.parse(output.toByteArray());
    assertEquals(0, document.getElementsByTagNameNS("*", "path").getLength());
    assertEquals(0, document.getElementsByTagNameNS("*", "text").getLength());
  }

  @Test
  void nullArgumentsAreRejectedBeforeWriting() {
    var output = new ByteArrayOutputStream();
    assertThrows(NullPointerException.class, () -> renderer.render(
        null,
        RenderOptions.DEFAULT,
        OutputFormat.PNG,
        output
    ));
    assertThrows(NullPointerException.class, () -> renderer.render(
        "x",
        null,
        OutputFormat.PNG,
        output
    ));
    assertThrows(NullPointerException.class, () -> renderer.render(
        "x",
        RenderOptions.DEFAULT,
        null,
        output
    ));
    assertThrows(NullPointerException.class, () -> renderer.render(
        "x",
        RenderOptions.DEFAULT,
        OutputFormat.PNG,
        null
    ));
    assertEquals(0, output.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\u0000", "", "", "", "￾", "￿", "\uD800", "\uDC00", "\uD800A", "A\uDC00"})
  void invalidTextIsRejectedWithoutPartialOutput(String text) {
    for (var format : OutputFormat.values()) {
      var output = new ByteArrayOutputStream();
      assertThrows(IllegalArgumentException.class, () -> renderer.render(
          text,
          RenderOptions.DEFAULT,
          format,
          output
      ));
      assertEquals(0, output.size());
    }
  }

  static Stream<Arguments> oversizedInputs() {
    return Stream.of(
        Arguments.of("input length", "x".repeat(1_000_001), "Diagram exceeds input size limit"),
        Arguments.of(
            "grid cells",
            (" ".repeat(501) + "\n").repeat(500),
            "Diagram exceeds cell limit"
        ),
        Arguments.of("image width", "x".repeat(4000), "Rendered dimensions exceed image limits"),
        Arguments.of("image height", "x\n".repeat(2400), "Rendered dimensions exceed image limits"),
        Arguments.of(
            "image area",
            (" ".repeat(1000) + "\n").repeat(200),
            "Rendered dimensions exceed image limits"
        )
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("oversizedInputs")
  void allocationLimitsAreCheckedBeforeOutput(String name, String text, String message) {
    for (var format : OutputFormat.values()) {
      var output = new ByteArrayOutputStream();
      var failure = assertThrows(IllegalArgumentException.class, () -> renderer.render(
          text,
          RenderOptions.DEFAULT,
          format,
          output
      ));
      assertEquals(message, failure.getMessage());
      assertEquals(0, output.size());
    }
  }

  @ParameterizedTest
  @ValueSource(doubles = {Double.MIN_VALUE, .01, .09, 1e20, Double.MAX_VALUE})
  void unrenderableScalesAreRejectedBeforeOutput(double scale) {
    var output = new ByteArrayOutputStream();
    assertThrows(IllegalArgumentException.class, () -> renderer.render(
        "x",
        new RenderOptions(scale, true, true),
        OutputFormat.PNG,
        output
    ));
    assertEquals(0, output.size());
  }

  @ParameterizedTest
  @EnumSource(OutputFormat.class)
  void writeFailuresRemainIoExceptionsAndTheStreamStaysOpen(OutputFormat format) {
    var failure = new IOException("destination unavailable");
    var output = new OutputStream() {
      @Override
      public void write(int value) throws IOException {
        throw failure;
      }

      @Override
      public void close() {
        fail("Closed the caller's stream");
      }
    };
    IOException thrown = assertThrows(IOException.class, () -> renderer.render(
        "+---+\n|   |\n+---+",
        RenderOptions.DEFAULT,
        format,
        output
    ));
    while (thrown.getCause() instanceof IOException) {
      thrown = (IOException) thrown.getCause();
    }
    assertSame(failure, thrown);
  }

  @Test
  void aMissingPngEncoderProducesAnIoException() {
    IIORegistry registry = IIORegistry.getDefaultInstance();
    var pngProviders = new ArrayList<ImageWriterSpi>();
    registry
      .getServiceProviders(ImageWriterSpi.class, true)
      .forEachRemaining(provider -> {
        if (Arrays.stream(provider.getFormatNames()).anyMatch("png"::equalsIgnoreCase)) {
          pngProviders.add(provider);
        }
      });
    assertFalse(pngProviders.isEmpty());
    try {
      pngProviders.forEach(registry::deregisterServiceProvider);
      IOException failure = assertThrows(IOException.class, () -> renderer.render(
          "",
          RenderOptions.DEFAULT,
          OutputFormat.PNG,
          new ByteArrayOutputStream()
      ));
      assertEquals("No PNG encoder available", failure.getMessage());
    } finally {
      pngProviders.forEach(registry::registerServiceProvider);
    }
  }

  @Test
  void emptyInputHasTheReportedDimensionsAndAWhiteBackground() throws Exception {
    var output = new ByteArrayOutputStream();
    ImageSize size =
        renderer.render("", new RenderOptions(1, false, true), OutputFormat.PNG, output);
    var image = ImageIO.read(new java.io.ByteArrayInputStream(output.toByteArray()));
    assertEquals(40, size.width());
    assertEquals(70, size.height());
    assertEquals(size.width(), image.getWidth());
    assertEquals(size.height(), image.getHeight());
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        assertEquals(0xffffffff, image.getRGB(x, y));
      }
    }
  }
}
