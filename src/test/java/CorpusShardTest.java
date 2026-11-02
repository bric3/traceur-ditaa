/* SPDX-License-Identifier: MPL-2.0 */
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;
import java.util.Locale;

class CorpusShardTest {
  @TableTest({
    "invalid",
    "'0'    ",
    "'0/0'  ",
    "'-1/4' ",
    "'4/4'  ",
    "'1/-2' ",
    "'a/4'  ",
    "'0/4/1'"
  })
  void rejectsInvalidShards(String invalid) {
    assertThrows(IllegalArgumentException.class, () -> CorpusShard.parse(invalid));
  }

  @Test
  void everyHashBelongsToExactlyOneShardRegardlessOfInputOrder() {
    for (int prefix : new int[] {0, 1, 2, 3, 4, Integer.MAX_VALUE, Integer.MIN_VALUE, -1}) {
      String hash = String.format(Locale.ROOT, "%08x", prefix) + "0".repeat(56);
      int matches = 0;
      for (int index = 0; index < 4; index++) {
        boolean included = CorpusShard.parse(index + "/4").includes(hash);
        assertEquals(Integer.toUnsignedLong(prefix) % 4 == index, included);
        if (included) {
          matches++;
        }
      }
      assertEquals(1, matches);
      assertTrue(CorpusShard.parse("0/1").includes(hash));
    }
  }
}
