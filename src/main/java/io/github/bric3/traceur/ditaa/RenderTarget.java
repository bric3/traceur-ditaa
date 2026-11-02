/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Objects;

/**
 * An image encoding and destination.
 *
 * <p>The caller controls buffering, flushing, closing and concurrent access. Rendering writes the
 * complete image and leaves the destination open and unflushed.
 */
public abstract class RenderTarget {
  private RenderTarget() {
  }

  /**
   * Creates a PNG target.
   * @param output destination for PNG bytes
   * @return a target writing to the supplied stream
   */
  public static RenderTarget png(OutputStream output) {
    Objects.requireNonNull(output, "output");
    return new RenderTarget() {
      @Override
      void write(Drawing drawing) throws IOException {
        PngWriter.write(drawing, output);
      }
    };
  }

  /**
   * Creates a UTF-8 SVG target.
   * @param output destination for UTF-8 SVG bytes
   * @return a target writing to the supplied stream
   */
  public static RenderTarget svg(OutputStream output) {
    Objects.requireNonNull(output, "output");
    return new RenderTarget() {
      @Override
      void write(Drawing drawing) throws IOException {
        SvgWriter.write(drawing, output);
      }
    };
  }

  /**
   * Creates a character SVG target. The supplied writer controls character encoding.
   * @param output destination for SVG characters
   * @return a target writing to the supplied writer
   */
  public static RenderTarget svg(Writer output) {
    Objects.requireNonNull(output, "output");
    return new RenderTarget() {
      @Override
      void write(Drawing drawing) throws IOException {
        SvgWriter.write(drawing, output);
      }
    };
  }

  abstract void write(Drawing drawing) throws IOException;
}
