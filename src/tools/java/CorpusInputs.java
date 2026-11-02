/* SPDX-License-Identifier: MPL-2.0 */
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared extraction order keeps pinned block numbers stable across discovery and replay.
 */
final class CorpusInputs {
  private CorpusInputs() {
  }

  static boolean raw(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    return lower.endsWith(".ditaa")
        || lower.endsWith(".txt")
        && (lower.contains("ditaa") || path.startsWith("test-resources/text/"));
  }

  static boolean supported(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    return !path.startsWith("/")
        && !path.contains("..")
        && lower.matches(".*\\.(ditaa|txt|md|adoc|puml|plantuml|org)");
  }

  static List<String> extract(String content, boolean upstreamDocumentation) {
    List<String> result = new ArrayList<>();
    Matcher wrapped = Pattern
      .compile("(?m)^@startditaa([^\\r\\n]*)\\R([\\s\\S]*?)^@endditaa[^\\r\\n]*")
      .matcher(content);
    while (wrapped.find()) {
      result.add(wrapped.group(2));
    }
    Matcher fenced =
        Pattern.compile("(?m)^```([^\\r\\n]*)\\R([\\s\\S]*?)^```[ \\t]*$").matcher(content);
    while (fenced.find()) {
      String lang = fenced.group(1).strip(), body = fenced.group(2);
      if ((lang.equals("ditaa") || upstreamDocumentation && lang.isEmpty())
          && body.contains("|")
          && !body.contains("@startditaa")) {
        result.add(body);
      }
    }
    Matcher adoc = Pattern
      .compile(
          "(?m)^\\[ditaa[^\\r\\n]*\\]\\R(?:\\.[^\\r\\n]*\\R)?([- .]{4,})\\R([\\s\\S]*?)^\\1[ \\t]*$"
      )
      .matcher(content);
    while (adoc.find()) {
      result.add(adoc.group(2));
    }
    Matcher org = Pattern
      .compile(
          "(?im)^[ \\t]*#\\+begin_src[ \\t]+ditaa[^\\r\\n]*\\R([\\s\\S]*?)^[ \\t]*#\\+end_src[ \\t]*$"
      )
      .matcher(content);
    while (org.find()) {
      result.add(org.group(1));
    }
    // Append newly supported fences after the legacy formats to preserve existing block indices.
    fenced.reset();
    while (fenced.find()) {
      if (fenced.group(1).strip().equals("ditaa")
          && !fenced.group(2).contains("|")
          && !fenced.group(2).contains("@startditaa")) {
        result.add(fenced.group(2));
      }
    }
    Matcher tilde =
        Pattern.compile("(?m)^~~~ditaa[ \\t]*\\R([\\s\\S]*?)^~~~[ \\t]*$").matcher(content);
    while (tilde.find()) {
      result.add(tilde.group(1));
    }
    return result;
  }
}
