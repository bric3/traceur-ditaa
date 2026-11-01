//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
// SPDX-License-Identifier: MPL-2.0

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Imports only .ditaa inputs from pinned GitHub code-search URLs into an external cache. */
class ImportSearchCorpus {
  public static void main(String[] args) throws Exception {
    if (args.length != 2)
      throw new IllegalArgumentException(
          "Usage: jbang tools/ImportSearchCorpus.java SEARCH_JSON EXTERNAL_CACHE");
    Path cache = Path.of(args[1]);
    Files.createDirectories(cache.resolve("inputs"));
    var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    var pattern =
        Pattern.compile(
            "\"url\":\"https://github.com/([^\"]+)/blob/([0-9a-f]{40})/([^\"]+\\.ditaa)\"");
    var matches = pattern.matcher(Files.readString(Path.of(args[0])));
    var jobs = new ArrayList<Callable<String>>();
    Map<String, Integer> counts = new TreeMap<>();
    while (matches.find()) {
      String repo = matches.group(1), revision = matches.group(2), path = matches.group(3);
      counts.merge(repo, 1, Integer::sum);
      jobs.add(
          () -> {
            try {
              String url =
                  "https://raw.githubusercontent.com/" + repo + "/" + revision + "/" + path;
              var request =
                  HttpRequest.newBuilder(URI.create(url))
                      .timeout(Duration.ofSeconds(30))
                      .header("User-Agent", "traceur-ditaa-corpus")
                      .build();
              var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
              if (response.statusCode() != 200)
                throw new IllegalStateException("HTTP " + response.statusCode());
              byte[] bytes = response.body();
              String hash =
                  HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
              Files.write(cache.resolve("inputs/" + hash + ".ditaa"), bytes);
              return hash
                  + "\t"
                  + repo
                  + "\t"
                  + revision
                  + "\t"
                  + path
                  + "\t1\t"
                  + hash
                  + "\tEXTERNAL_ONLY_CHECK_UPSTREAM_TERMS\n";
            } catch (Exception e) {
              return "ERROR\t"
                  + repo
                  + "\t"
                  + revision
                  + "\t"
                  + path
                  + "\t"
                  + e.getMessage().replace('\n', ' ')
                  + "\n";
            }
          });
    }
    var executor = Executors.newFixedThreadPool(8);
    StringBuilder manifest =
        new StringBuilder("id\trepository\trevision\tpath\tblock\tsha256\tlicence\n");
    try {
      for (var result : executor.invokeAll(jobs)) manifest.append(result.get());
    } finally {
      executor.shutdownNow();
    }
    Files.writeString(cache.resolve("search-manifest.tsv"), manifest);
    StringBuilder inventory = new StringBuilder("repository\tsearchResults\n");
    counts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .forEach(e -> inventory.append(e.getKey()).append('\t').append(e.getValue()).append('\n'));
    Files.writeString(cache.resolve("search-inventory.tsv"), inventory);
    long unique;
    try (var files = Files.list(cache.resolve("inputs"))) {
      unique = files.count();
    }
    System.out.println(
        "Imported "
            + jobs.size()
            + " search results from "
            + counts.size()
            + " repositories; combined unique inputs="
            + unique);
    System.out.println(inventory.substring(0, Math.min(inventory.length(), 1200)));
  }
}
