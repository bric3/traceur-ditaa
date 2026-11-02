//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
// SPDX-License-Identifier: MPL-2.0
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Selects up to 500 new inputs in repository order, taking one input per repository each round.
 */
class SelectCorpus {
  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException("BASE_MANIFEST EXTERNAL_CACHE REJECTED_MANIFEST");
    }
    Path cache = Path.of(args[1]);
    Set<String> seen = new HashSet<>();
    for (String row : Files.readAllLines(Path.of(args[0]))) {
      if (!row.startsWith("id\t")) {
        seen.add(row.split("\t", -1)[0]);
      }
    }
    int retained = seen.size();
    Set<String> rejected = new HashSet<>();
    for (String row : Files.readAllLines(Path.of(args[2]))) {
      if (row.startsWith("id\t")) {
        continue;
      }
      String[] fields = row.split("\t", -1);
      if (fields.length != 8
          || !fields[0].matches("[0-9a-f]{64}")
          || !fields[7].equals("EMPTY_DIAGRAM_REJECTED_BY_BOTH_REFERENCES")
          || !Files.readString(cache.resolve("inputs/" + fields[0] + ".ditaa")).isBlank()) {
        throw new IllegalArgumentException(
            "Rejections require an empty input rejected by both references"
        );
      }
      rejected.add(fields[0]);
    }
    Map<String, ArrayDeque<String>> repositories = new TreeMap<>();
    int errors = 0;
    for (String row : Files.readAllLines(cache.resolve("search-manifest.tsv"))) {
      if (row.startsWith("id\t")) {
        continue;
      }
      if (row.startsWith("ERROR\t")) {
        errors++;
        continue;
      }
      String[] fields = row.split("\t", -1);
      if (fields.length != 7
          || !fields[0].matches("[0-9a-f]{64}")
          || !fields[0].equals(fields[5])) {
        throw new IllegalArgumentException("Invalid imported provenance row");
      }
      if (seen.add(fields[0])) {
        repositories
          .computeIfAbsent(fields[1], ignored -> new ArrayDeque<>())
          .add(row);
      }
    }
    String header = "id\trepository\trevision\tpath\tblock\tsha256\tlicence";
    List<String> candidates = new ArrayList<>();
    candidates.add(header);
    boolean remaining;
    do {
      remaining = false;
      for (var queue : repositories.values()) {
        if (!queue.isEmpty()) {
          candidates.add(queue.removeFirst());
          remaining = true;
        }
      }
    } while (remaining);
    Files.write(cache.resolve("candidates.tsv"), candidates);
    Path inputs = Files.createDirectories(cache.resolve("selected/inputs"));
    List<String> selected = new ArrayList<>();
    selected.add(header);
    Set<String> selectedHashes = new HashSet<>();
    for (String row : candidates.subList(1, candidates.size())) {
      String hash = row.split("\t", -1)[0];
      if (rejected.contains(hash)) {
        continue;
      }
      byte[] bytes = Files.readAllBytes(cache.resolve("inputs/" + hash + ".ditaa"));
      String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      if (!hash.equals(actual)) {
        throw new IllegalStateException("Cached input checksum differs: " + hash);
      }
      Files.write(inputs.resolve(hash + ".ditaa"), bytes);
      selected.add(row);
      selectedHashes.add(hash);
      if (selectedHashes.size() == 500) {
        break;
      }
    }
    try (var files = Files.list(inputs)) {
      for (Path file : files.toList()) {
        String name = file.getFileName().toString();
        if (name.matches("[0-9a-f]{64}\\.ditaa")
            && !selectedHashes.contains(name.substring(0, 64))) {
          Files.delete(file);
        }
      }
    }
    Files.write(cache.resolve("selected/manifest.tsv"), selected);
    System.out.println(
        "Retained="
        + retained
        + " candidates="
        + (candidates.size() - 1)
        + " repositories="
        + repositories.size()
        + " selected="
        + selectedHashes.size()
        + " fetchErrors="
        + errors
    );
  }
}
