/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;

class RenderTargetTest {
  private final DitaaRenderer renderer = new DitaaRenderer();

  @TableTest({
    "format | reader | writer",
    "PNG    | false  | false ",
    "PNG    | true   | false ",
    "SVG    | false  | false ",
    "SVG    | true   | false ",
    "SVG    | false  | true  ",
    "SVG    | true   | true  "
  })
  void targetsMatchTheExistingApiAndKeepResourcesOpen(
      OutputFormat format,
      boolean reader,
      boolean writer
  ) throws Exception {
    String diagram = "\r\n+---------+\r\n| é <& 😀 |\r\n+---------+\r\n";
    var options = new RenderOptions(1.5, false, false);
    var expected = new ByteArrayOutputStream();
    var expectedSize = renderer.render(diagram, options, format, expected);
    var bytes = new ByteArrayOutputStream() {
      @Override
      public void flush() {
        fail("Destination flushed");
      }

      @Override
      public void close() {
        fail("Destination closed");
      }
    };
    var characters = new StringWriter() {
      @Override
      public void flush() {
        fail("Destination flushed");
      }

      @Override
      public void close() {
        fail("Destination closed");
      }
    };
    var input = new StringReader(diagram) {
      @Override
      public int read(char[] buffer, int offset, int length) throws IOException {
        return super.read(buffer, offset, Math.min(1, length));
      }

      @Override
      public void close() {
        fail("Input closed");
      }
    };
    RenderTarget target = writer
        ? RenderTarget.svg(characters)
        : format == OutputFormat.PNG ? RenderTarget.png(bytes) : RenderTarget.svg(bytes);
    ImageSize size =
        reader ? renderer.render(input, options, target) : renderer.render(diagram, options, target);
    assertEquals(expectedSize.width(), size.width());
    assertEquals(expectedSize.height(), size.height());
    assertArrayEquals(
        expected.toByteArray(),
        writer ? characters.toString().getBytes(UTF_8) : bytes.toByteArray()
    );
    if (reader) {
      assertEquals(-1, input.read());
    }
  }

  @Test
  void inputStreamDecodingBelongsToTheCaller() throws Exception {
    var input = new InputStreamReader(new ByteArrayInputStream("café".getBytes(UTF_8)), UTF_8);
    var output = new StringWriter();
    renderer.render(input, RenderOptions.DEFAULT, RenderTarget.svg(output));
    assertTrue(output.toString().contains("café</text>"));
  }

  @Test
  void callerControlsWriterEncodingAndFlushing() throws Exception {
    var bytes = new ByteArrayOutputStream();
    var output = new BufferedWriter(new OutputStreamWriter(bytes, UTF_16LE));
    renderer.render("café", RenderOptions.DEFAULT, RenderTarget.svg(output));
    assertEquals(0, bytes.size());
    output.flush();
    var actual = new String(bytes.toByteArray(), UTF_16LE);
    var expected = new StringWriter();
    renderer.render("café", RenderOptions.DEFAULT, RenderTarget.svg(expected));
    assertEquals(expected.toString(), actual);
  }

  @TableTest({
    "length  | accepted",
    "0       | true    ",
    "8191    | true    ",
    "8192    | true    ",
    "8193    | true    ",
    "1000000 | true    ",
    "1000001 | false   "
  })
  void readerInputLimitsIncludeBufferAndEofBoundaries(int length, boolean accepted) throws Exception {
    var output = new StringWriter();
    var input = new StringReader("\n".repeat(length));
    if (accepted) {
      renderer.render(input, RenderOptions.DEFAULT, RenderTarget.svg(output));
      assertTrue(output.toString().endsWith("</svg>\n"));
      assertEquals(-1, input.read());
    } else {
      var failure = assertThrows(IllegalArgumentException.class, () -> renderer.render(
          input,
          RenderOptions.DEFAULT,
          RenderTarget.svg(output)
      ));
      assertEquals("Diagram exceeds input size limit", failure.getMessage());
      assertEquals("", output.toString());
    }
  }

  @Test
  void unboundedInputStopsAfterTheFirstExcessCharacter() {
    var output = new StringWriter();
    var input = new Reader() {
      int consumed;

      @Override
      public int read(char[] buffer, int offset, int length) {
        consumed += length;
        assertTrue(consumed <= 1_000_001, "Read beyond the input limit");
        Arrays.fill(buffer, offset, offset + length, '\n');
        return length;
      }

      @Override
      public void close() {
        fail("Input closed");
      }
    };
    assertThrows(IllegalArgumentException.class, () -> renderer.render(
        input,
        RenderOptions.DEFAULT,
        RenderTarget.svg(output)
    ));
    assertEquals(1_000_001, input.consumed);
    assertEquals("", output.toString());
  }

  @Test
  void readFailuresPropagateBeforeWriting() {
    var failure = new IOException("read failed");
    var output = new StringWriter();
    var input = new Reader() {
      @Override
      public int read(char[] buffer, int offset, int length) throws IOException {
        throw failure;
      }

      @Override
      public void close() {
        fail("Input closed");
      }
    };
    assertSame(
        failure,
        assertThrows(IOException.class, () -> renderer.render(
            input,
            RenderOptions.DEFAULT,
            RenderTarget.svg(output)
        ))
    );
    assertEquals("", output.toString());
  }

  @Test
  void writerFailuresPropagateWithoutClosingOrFlushing() {
    var failure = new IOException("write failed");
    var output = new Writer() {
      @Override
      public void write(char[] chars, int offset, int length) throws IOException {
        throw failure;
      }

      @Override
      public void flush() {
        fail("Destination flushed");
      }

      @Override
      public void close() {
        fail("Destination closed");
      }
    };
    assertSame(
        failure,
        assertThrows(IOException.class, () -> renderer.render(
            "x",
            RenderOptions.DEFAULT,
            RenderTarget.svg(output)
        ))
    );
  }

  @Test
  void nullArgumentsFailBeforeReadingOrWriting() {
    var input = new Reader() {
      @Override
      public int read(char[] buffer, int offset, int length) {
        return fail("Input read");
      }

      @Override
      public void close() {
        fail("Input closed");
      }
    };
    var target = RenderTarget.svg(new StringWriter());
    assertThrows(NullPointerException.class, () -> RenderTarget.png(null));
    assertThrows(NullPointerException.class, () -> RenderTarget.svg((OutputStream) null));
    assertThrows(NullPointerException.class, () -> RenderTarget.svg((Writer) null));
    assertThrows(NullPointerException.class, () -> renderer.render(
        (String) null,
        RenderOptions.DEFAULT,
        target
    ));
    assertThrows(NullPointerException.class, () -> renderer.render(
        (Reader) null,
        RenderOptions.DEFAULT,
        target
    ));
    assertThrows(NullPointerException.class, () -> renderer.render(input, null, target));
    assertThrows(NullPointerException.class, () -> renderer.render(
        input,
        RenderOptions.DEFAULT,
        null
    ));
    assertThrows(NullPointerException.class, () -> renderer.render("x", null, target));
    assertThrows(NullPointerException.class, () -> renderer.render("x", RenderOptions.DEFAULT, null));
  }
}
