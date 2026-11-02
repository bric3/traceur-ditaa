/* SPDX-License-Identifier: MPL-2.0 */
/**
 * Stable content-based partitioning shared by fetching and comparison.
 */
final class CorpusShard {
  private final int index;
  private final int count;

  private CorpusShard(int index, int count) {
    this.index = index;
    this.count = count;
  }

  static CorpusShard parse(String value) {
    String[] fields = value.split("/", -1);
    if (fields.length != 2) {
      throw new IllegalArgumentException("Shard must be INDEX/COUNT");
    }
    int index = Integer.parseInt(fields[0]), count = Integer.parseInt(fields[1]);
    if (index < 0 || count < 1 || index >= count) {
      throw new IllegalArgumentException("Invalid shard: " + value);
    }
    return new CorpusShard(index, count);
  }

  boolean includes(String sha256) {
    return Long.parseLong(sha256.substring(0, 8), 16) % count == index;
  }
}
