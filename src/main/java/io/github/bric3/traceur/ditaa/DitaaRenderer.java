/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Renders raw ditaa text. PlantUML preprocessing and start/end directives belong to the caller.
 * Instances are stateless and thread-safe. The caller owns the output stream.
 */
public final class DitaaRenderer {
  /** Creates a stateless renderer. */
  public DitaaRenderer() {}

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
      String text, RenderOptions options, OutputFormat format, OutputStream output)
      throws IOException {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(output, "output");

    Drawing drawing = DiagramParser.parse(text, options);
    if (format == OutputFormat.PNG) {
      PngWriter.write(drawing, output);
    } else {
      SvgWriter.write(drawing, output);
    }
    return new ImageSize(drawing.width, drawing.height);
  }
}
