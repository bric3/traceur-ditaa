//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//SOURCES ../src/tools/java/CorpusInputs.java
// SPDX-License-Identifier: MPL-2.0
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Imports diagram and documentation files from pinned GitHub code-search results.
 */
class ImportSearchCorpus {
  record Source(String repository, String revision, String path) {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("SEARCH_JSON_OR_TSV EXTERNAL_CACHE");
    }
    Path cache = Path.of(args[1]);
    Files.createDirectories(cache.resolve("inputs"));
    Files.createDirectories(cache.resolve("sources"));
    var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    var pattern = Pattern.compile(
        "https://github.com/([^/\\\"\\s]+/[^/\\\"\\s]+)/blob/([0-9a-f]{40})/([^\\\"\\r\\n]+)"
    );
    String search = Files.readString(Path.of(args[0]));
    var sources = new TreeMap<String, Source>();
    if (args[0].endsWith(".tsv")) {
      for (String row : search.lines().toList()) {
        String[] fields = row.split("\t", -1);
        if (fields.length != 3) {
          throw new IllegalArgumentException("Invalid search row");
        }
        var match = pattern.matcher(fields[2]);
        if (!match.matches() || !fields[0].equals(match.group(1))) {
          throw new IllegalArgumentException("Search URL does not match its repository");
        }
        if (CorpusInputs.supported(fields[1])) {
          sources.put(fields[0] + "/" + fields[1], new Source(fields[0], match.group(2), fields[1]));
        }
      }
    } else {
      var matches = pattern.matcher(search);
      while (matches.find()) {
        String repo = matches.group(1), revision = matches.group(2), path = matches.group(3);
        if (CorpusInputs.supported(path)) {
          sources.put(repo + "/" + path, new Source(repo, revision, path));
        }
      }
    }
    var jobs = new ArrayList<Callable<String>>();
    for (Source source : sources.values()) {
      jobs.add(() -> {
        try {
          Path cached = cache.resolve(
              "sources/"
              + sha256((source.repository + source.revision + source.path)
                .getBytes(StandardCharsets.UTF_8)
              )
              + ".txt"
          );
          byte[] bytes;
          if (Files.exists(cached)) {
            bytes = Files.readAllBytes(cached);
          } else {
            var url = new URI(
                "https",
                "raw.githubusercontent.com",
                "/" + source.repository + "/" + source.revision + "/" + source.path,
                null
            );
            var request = HttpRequest
              .newBuilder(url)
              .timeout(Duration.ofSeconds(30))
              .header("User-Agent", "traceur-ditaa-corpus")
              .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
              throw new IllegalStateException("HTTP " + response.statusCode());
            }
            bytes = response.body();
            Files.write(cached, bytes);
          }
          List<byte[]> blocks = CorpusInputs.raw(source.path)
              ? List.of(bytes)
              : CorpusInputs
            .extract(
                new String(bytes, StandardCharsets.UTF_8),
                source.repository.equals("stathissideris/ditaa") && source.path.equals("README.md")
            )
            .stream()
            .map(text -> text.getBytes(StandardCharsets.UTF_8))
            .toList();
          var rows = new StringBuilder();
          for (int block = 0; block < blocks.size(); block++) {
            String hash = sha256(blocks.get(block));
            Files.write(cache.resolve("inputs/" + hash + ".ditaa"), blocks.get(block));
            rows
              .append(hash)
              .append('\t')
              .append(source.repository)
              .append('\t')
              .append(source.revision)
              .append('\t')
              .append(source.path)
              .append('\t')
              .append(block + 1)
              .append('\t')
              .append(hash)
              .append("\tEXTERNAL_ONLY_CHECK_UPSTREAM_TERMS\n");
          }
          return rows.toString();
        } catch (Exception error) {
          return "ERROR\t"
              + source.repository
              + "\t"
              + source.revision
              + "\t"
              + source.path
              + "\t"
              + error.getMessage().replace('\n', ' ').replace('\t', ' ')
              + "\n";
        }
      });
    }
    var manifest = new StringBuilder("id\trepository\trevision\tpath\tblock\tsha256\tlicence\n");
    try (var executor = Executors.newFixedThreadPool(8)) {
      for (var result : executor.invokeAll(jobs)) {
        manifest.append(result.get());
      }
    }
    Files.writeString(cache.resolve("search-manifest.tsv"), manifest);
    System.out.println(
        "Imported " + sources.size() + " source files; manifest: " + cache.resolve(
            "search-manifest.tsv"
        )
    );
  }

  static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
