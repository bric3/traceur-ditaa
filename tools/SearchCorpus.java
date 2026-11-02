//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
// SPDX-License-Identifier: MPL-2.0
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Records bounded GitHub searches without downloading implementation sources.
 */
class SearchCorpus {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("EXTERNAL_CACHE");
    }
    Path cache = Files.createDirectories(Path.of(args[0]));
    var queries = List.of(
        "extension:ditaa",
        "\"[ditaa\" extension:adoc",
        "\"@startditaa\" extension:puml",
        "\"@startditaa\" extension:plantuml",
        "\"ditaa\" extension:md",
        "\"begin_src ditaa\" extension:org"
    );
    var entries = new TreeSet<String>();
    Path inventoryPath = cache.resolve("search-queries.tsv");
    var inventory = new StringBuilder(
        Files.exists(inventoryPath)
        ? Files.readString(inventoryPath)
        : "query\tlimit\tresults\tpossiblyTruncated\tsearchedAt\n"
    );
    for (int i = 0; i < queries.size(); i++) {
      String query = queries.get(i);
      Path output = cache.resolve("search-" + i + ".tsv");
      if (Files.exists(output) && inventory.toString().contains(query + "\t1000\t")) {
        entries.addAll(Files.readAllLines(output));
        continue;
      }
      Path error = cache.resolve("search-" + i + ".log");
      for (int attempt = 0; ; attempt++) {
        var process = new ProcessBuilder(
            "gh",
            "search",
            "code",
            query,
            "--limit",
            "1000",
            "--json",
            "repository,path,url",
            "--jq",
            ".[] | [.repository.nameWithOwner, .path, .url] | @tsv"
        )
          .redirectOutput(output.toFile())
          .redirectError(error.toFile())
          .start();
        if (process.waitFor() == 0) {
          break;
        }
        String detail = Files.readString(error);
        if (attempt >= 3 || !detail.contains("rate limit")) {
          throw new IllegalStateException("Search failed: " + query + " " + detail);
        }
        System.out.println("GitHub rate limit; retrying in 60 seconds: " + query);
        Thread.sleep(60_000);
      }
      var rows = Files.readAllLines(output);
      entries.addAll(rows);
      inventory
        .append(query)
        .append("\t1000\t")
        .append(rows.size())
        .append('\t')
        .append(rows.size() == 1000)
        .append('\t')
        .append(Instant.now())
        .append('\n');
      Files.writeString(inventoryPath, inventory);
      System.out.println(query + ": " + rows.size() + " results");
    }
    Files.write(cache.resolve("search.tsv"), entries);
    System.out.println("Unique source files: " + entries.size());
  }
}
