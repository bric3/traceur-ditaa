/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class DiagramFixtures {
  static byte[] read(String name) throws IOException {
    try (var input = DiagramFixtures.class.getResourceAsStream("/compatibility/" + name)) {
      assertNotNull(input, "Missing reference fixture: " + name);
      return input.readAllBytes();
    }
  }

  static String text(String name) throws IOException {
    return new String(read(name + ".ditaa"), StandardCharsets.UTF_8);
  }

  static String layout(String name, int count, int index) throws IOException {
    String[] layouts = text(name).stripTrailing().split("\n\n");
    assertEquals(count, layouts.length);
    return layouts[index];
  }
}
