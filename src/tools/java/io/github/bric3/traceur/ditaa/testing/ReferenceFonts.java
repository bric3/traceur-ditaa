/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa.testing;

/**
 * Keeps macOS Java 11 glyph cache state consistent in reference fixture processes and tests.
 */
public final class ReferenceFonts {
  public static final String PRIMER =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789";

  private ReferenceFonts() {
  }

  public static boolean requiresWarmup() {
    return Runtime.version().feature() == 11 && System.getProperty("os.name").equals("Mac OS X");
  }
}
