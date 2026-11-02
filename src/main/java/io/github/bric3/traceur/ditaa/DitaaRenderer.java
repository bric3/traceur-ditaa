/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Objects;

/**
 * Renders raw ditaa text. PlantUML preprocessing and start/end directives belong to the caller.
 * Instances are stateless and thread-safe. The caller owns input and output resources.
 */
public final class DitaaRenderer {
  /**
   * Creates a stateless renderer.
   */
  public DitaaRenderer() {
  }

  /**
   * Renders one diagram without flushing or closing the destination.
   *
   * @param text raw diagram text, with whitespace preserved
   * @param options scale, shadow, and shared-edge separation settings
   * @param target image encoding and destination
   * @return encoded image dimensions
   * @throws IOException when encoding or writing fails
   * @throws IllegalArgumentException when input or output dimensions exceed rendering limits
   */
  public ImageSize render(String text, RenderOptions options, RenderTarget target)
      throws IOException {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(target, "target");
    Drawing drawing = DiagramParser.parse(text, options);
    target.write(drawing);
    return new ImageSize(drawing.width, drawing.height);
  }

  /**
   * Reads a diagram to EOF and renders it. Input is limited to 1,000,000 UTF-16 code units and is
   * validated before writing. The caller owns buffering and input decoding, for example through an
   * {@link java.io.InputStreamReader}. Rendering never closes the reader or flushes or closes the
   * destination.
   *
   * @param input raw diagram characters, with whitespace preserved
   * @param options scale, shadow, and shared-edge separation settings
   * @param target image encoding and destination
   * @return encoded image dimensions
   * @throws IOException when reading, encoding or writing fails
   * @throws IllegalArgumentException when input or output dimensions exceed rendering limits
   */
  public ImageSize render(Reader input, RenderOptions options, RenderTarget target)
      throws IOException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(target, "target");
    var text = new StringBuilder();
    var buffer = new char[8192];
    int count;
    while ((count = input.read(
        buffer,
        0,
        Math.min(buffer.length, DiagramParser.MAX_INPUT_CHARS - text.length() + 1)
    )) != -1) {
      if (text.length() + count > DiagramParser.MAX_INPUT_CHARS) {
        throw new IllegalArgumentException("Diagram exceeds input size limit");
      }
      text.append(buffer, 0, count);
    }
    return render(text.toString(), options, target);
  }

  /**
   * Renders one diagram. The caller retains ownership of the supplied stream.
   *
   * @param text raw diagram text, with whitespace preserved
   * @param options scale, shadow, and shared-edge separation settings
   * @param format image encoding
   * @param output destination for encoded bytes
   * @return encoded image dimensions
   * @throws IOException when encoding or writing fails
   * @throws IllegalArgumentException when input or output dimensions exceed rendering limits
   */
  public ImageSize render(
      String text,
      RenderOptions options,
      OutputFormat format,
      OutputStream output
  ) throws IOException {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(output, "output");

    return render(
        text,
        options,
        format == OutputFormat.PNG ? RenderTarget.png(output) : RenderTarget.svg(output)
    );
  }
}
