/* SPDX-License-Identifier: MPL-2.0 */
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import io.github.bric3.traceur.ditaa.OutputFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class CorpusCompatibilityTest {
  @TestFactory
  List<DynamicTest> corpus() throws Exception {
    Path inputs = Path.of(System.getProperty("corpus.directory"));
    List<String> rows = Files.readAllLines(Path.of(System.getProperty("corpus.manifest")));
    List<DynamicTest> tests = new ArrayList<>();
    var comparison = new CorpusComparison(
        Path.of(System.getProperty("corpus.pngReference")),
        Path.of(System.getProperty("corpus.svgReference")),
        Path.of(System.getProperty("corpus.report")),
        System.getProperty("corpus.rasterizer"),
        System.getProperty("corpus.rendererClasspath")
    );
    var seen = new HashSet<String>();
    for (String row : rows.subList(1, rows.size())) {
      String id = row.substring(0, row.indexOf('\t'));
      if (!seen.add(id)) {
        continue;
      }
      Path input = inputs.resolve(id + ".ditaa");
      if (!Files.isRegularFile(input)
          || !CorpusComparison.sha256(Files.readAllBytes(input)).equals(id)) {
        throw new IllegalArgumentException("Missing or changed corpus input: " + input);
      }
      for (int variant = 0; variant < 4; variant++) {
        int option = variant;
        for (OutputFormat format : OutputFormat.values()) {
          tests.add(
              dynamicTest(id + "-" + variant + "-" + format, () -> comparison.compare(
                  input,
                  option,
                  format
              ))
          );
        }
      }
    }
    if (tests.isEmpty()) {
      tests.add(dynamicTest("empty slot", () -> assumeTrue(false, "No corpus inputs selected")));
    }
    return tests;
  }
}
