/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

/**
 * Immutable rendering options. Instances can be shared between threads.
 */
public final class RenderOptions {
  /**
   * Unit scale with shadows and shared-edge separation enabled.
   */
  public static final RenderOptions DEFAULT = new RenderOptions(1, true, true);
  private final double scale;
  private final boolean shadows;
  private final boolean separation;

  /**
   * Creates rendering options.
   *
   * @param scale finite positive scale factor; scaled cell dimensions are rounded down
   * @param shadows whether closed shapes cast shadows
   * @param separation whether adjacent closed shapes have separate outlines
   * @throws IllegalArgumentException when scale is not finite and positive
   */
  public RenderOptions(double scale, boolean shadows, boolean separation) {
    if (!Double.isFinite(scale) || scale <= 0) {
      throw new IllegalArgumentException("Scale must be finite and positive");
    }
    this.scale = scale;
    this.shadows = shadows;
    this.separation = separation;
  }

  /**
   * Returns the requested scale factor.
   *
   * @return the requested scale factor
   */
  public double scale() {
    return scale;
  }

  /**
   * Returns whether shadows are enabled.
   *
   * @return whether shadows are enabled
   */
  public boolean shadows() {
    return shadows;
  }

  /**
   * Returns whether shared-edge separation is enabled.
   *
   * @return whether shared-edge separation is enabled
   */
  public boolean separation() {
    return separation;
  }
}
