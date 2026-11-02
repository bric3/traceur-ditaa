/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import org.junit.jupiter.params.converter.SimpleArgumentConverter;

/**
 * Tables spell row breaks as \n so diagram spacing remains visible.
 */
public final class DiagramConverter extends SimpleArgumentConverter {
  @Override
  protected Object convert(Object source, Class<?> targetType) {
    return ((String) source).replace("\\n", "\n");
  }
}
