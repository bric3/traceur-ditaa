//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
// SPDX-License-Identifier: MPL-2.0

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/**
 * Fetches diagram inputs and documentation only. Never fetches Java or other implementation
 * sources.
 */
class FetchCorpus {
  static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  static final Pattern PATH =
      // shortcut: discovery handles ASCII paths; use a JSON parser for escaped repository paths.
      Pattern.compile("\"path\"\\s*:\\s*\"([A-Za-z0-9_./+ -]+)\"[^}]*?\"type\"\\s*:\\s*\"blob\"");
  static final Pattern SHA = Pattern.compile("\"sha\"\\s*:\\s*\"([0-9a-f]{40})\"");

  public static void main(String[] args) throws Exception {
    if (args.length != 1)
      throw new IllegalArgumentException("Usage: jbang tools/FetchCorpus.java EXTERNAL_CACHE");
    Path cache = Path.of(args[0]).toAbsolutePath();
    Files.createDirectories(cache.resolve("inputs"));
    var manifest = new StringBuilder("id\trepository\trevision\tpath\tblock\tsha256\tlicence\n");
    var inventory =
        new StringBuilder("repository\trevision\tcandidateFiles\textractedInputs\tuniqueInputs\n");
    Set<String> seen = new HashSet<>();
    for (String repo :
        List.of(
            "stathissideris/ditaa",
            "plantuml/plantuml",
            "asciidoctor/asciidoctor-diagram",
            "yuzutech/kroki",
            "asciidoctor/asciidoctor-maven-examples")) {
      try {
        String metadata = get("https://api.github.com/repos/" + repo);
        var branch = Pattern.compile("\"default_branch\"\\s*:\\s*\"([^\"]+)\"").matcher(metadata);
        if (!branch.find()) throw new IllegalStateException("Missing default branch");
        String tree =
            get(
                "https://api.github.com/repos/"
                    + repo
                    + "/git/trees/"
                    + branch.group(1)
                    + "?recursive=1");
        Files.writeString(cache.resolve(repo.replace('/', '-') + "-tree.json"), tree);
        if (tree.contains("\"truncated\":true") || tree.contains("\"truncated\": true"))
          throw new IllegalStateException("Truncated tree");
        var sha = SHA.matcher(tree);
        if (!sha.find()) throw new IllegalStateException("Missing revision");
        String revision = sha.group(1);
        int candidates = 0, extracted = 0, unique = 0;
        Matcher paths = PATH.matcher(tree);
        while (paths.find()) {
          String path = paths.group(1);
          String lower = path.toLowerCase(Locale.ROOT);
          boolean raw =
              repo.equals("stathissideris/ditaa")
                  && path.startsWith("test-resources/text/")
                  && lower.endsWith(".txt");
          boolean extension =
              lower.endsWith(".ditaa")
                  || lower.endsWith(".puml")
                  || lower.endsWith(".plantuml")
                  || lower.endsWith(".adoc")
                  || lower.endsWith(".md")
                  || lower.endsWith(".txt");
          boolean selected =
              raw
                  || extension
                      && (lower.contains("ditaa")
                          || lower.endsWith(".adoc")
                          || lower.endsWith(".md")
                          || lower.endsWith(".puml")
                          || lower.endsWith(".plantuml"));
          if (!selected) continue;
          candidates++;
          String url =
              "https://raw.githubusercontent.com/"
                  + repo
                  + "/"
                  + revision
                  + "/"
                  + path.replace(" ", "%20");
          String content = get(url);
          List<String> inputs =
              raw || lower.endsWith(".ditaa") || lower.endsWith(".txt") && lower.contains("ditaa")
                  ? List.of(content)
                  : extract(
                      content, repo.equals("stathissideris/ditaa") && path.equals("README.md"));
          int block = 0;
          for (String input : inputs) {
            block++;
            extracted++;
            String hash =
                HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8)));
            if (seen.add(hash)) {
              unique++;
              Files.writeString(cache.resolve("inputs/" + hash + ".ditaa"), input);
            }
            manifest
                .append(hash)
                .append('\t')
                .append(repo)
                .append('\t')
                .append(revision)
                .append('\t')
                .append(path)
                .append('\t')
                .append(block)
                .append('\t')
                .append(hash)
                .append("\tEXTERNAL_ONLY_CHECK_UPSTREAM_TERMS\n");
          }
        }
        inventory
            .append(repo)
            .append('\t')
            .append(revision)
            .append('\t')
            .append(candidates)
            .append('\t')
            .append(extracted)
            .append('\t')
            .append(unique)
            .append('\n');
        System.out.println(
            repo
                + ": "
                + candidates
                + " candidate files, "
                + extracted
                + " inputs, "
                + unique
                + " new unique inputs");
      } catch (Exception e) {
        inventory
            .append(repo)
            .append("\tERROR\t")
            .append(e.getMessage().replace('\n', ' '))
            .append("\n");
        System.err.println(repo + ": " + e);
      }
    }
    Files.writeString(cache.resolve("manifest.tsv"), manifest);
    Files.writeString(cache.resolve("inventory.tsv"), inventory);
    System.out.println("Unique corpus: " + seen.size() + "; " + cache);
  }

  static String get(String url) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "traceur-ditaa-corpus")
            .timeout(java.time.Duration.ofSeconds(30))
            .build();
    var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200)
      throw new IllegalStateException(response.statusCode() + " " + url);
    return response.body();
  }

  static List<String> extract(String content, boolean upstreamDocumentation) {
    List<String> result = new ArrayList<>();
    Matcher wrapped =
        Pattern.compile("(?m)^@startditaa([^\\r\\n]*)\\R([\\s\\S]*?)^@endditaa[^\\r\\n]*")
            .matcher(content);
    while (wrapped.find()) result.add(wrapped.group(2));
    Matcher fenced =
        Pattern.compile("(?m)^```([^\\r\\n]*)\\R([\\s\\S]*?)^```[ \\t]*$").matcher(content);
    while (fenced.find()) {
      String lang = fenced.group(1).strip(), body = fenced.group(2);
      if ((lang.equals("ditaa") || upstreamDocumentation && lang.isEmpty())
          && body.contains("|")
          && !body.contains("@startditaa")) result.add(body);
    }
    Matcher adoc =
        Pattern.compile(
                "(?m)^\\[ditaa[^\\r"
                    + "\\n"
                    + "]*\\]\\R(?:\\.[^\\r"
                    + "\\n"
                    + "]*\\R)?([- .]{4,})\\R([\\s\\S]*?)^\\1[ \\t]*$")
            .matcher(content);
    while (adoc.find()) result.add(adoc.group(2));
    return result;
  }
}
