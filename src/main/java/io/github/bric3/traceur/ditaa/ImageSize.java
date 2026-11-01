/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

/** Dimensions of the encoded image, in pixels. */
public final class ImageSize {
  private final int width;
  private final int height;

  ImageSize(int width, int height) {
    this.width = width;
    this.height = height;
  }

  /**
   * Returns the encoded width in pixels.
   *
   * @return encoded width in pixels
   */
  public int width() {
    return width;
  }

  /**
   * Returns the encoded height in pixels.
   *
   * @return encoded height in pixels
   */
  public int height() {
    return height;
  }
}
