/* SPDX-License-Identifier: MPL-2.0 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Interleaved fresh-JVM measurements. No JVM warmup is hidden from the measurement.
 */
public final class ColdStartBenchmark {
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Supply -Poracle=PLANTUML_JAR -PsvgOracle=DITAA_JAR");
    }
    Path oracle = Path.of(args[0]).toAbsolutePath();
    Path svgOracle = Path.of(args[1]).toAbsolutePath();
    Path dir = Files.createDirectories(Path
      .of(System.getProperty("benchmark.directory", "build/cold-start"))
      .toAbsolutePath()
    );
    StringBuilder results =
        new StringBuilder(
            "size\tformat\timplementation\tmedianMs\tp95Ms\tmedianRssMiB\tp95RssMiB\n"
    );
    StringBuilder samples =
        new StringBuilder("size\tformat\timplementation\tround\tnanos\trssBytes\n");
    String os = System.getProperty("os.name");
    boolean mac = os.startsWith("Mac");
    boolean memory = Files.isExecutable(Path.of("/usr/bin/time"))
        && (mac || os.startsWith("Linux"));
    Files.writeString(
        dir.resolve("environment.txt"),
        "java="
        + System.getProperty("java.runtime.version")
        + "\njava.home="
        + System.getProperty("java.home")
        + "\nos="
        + os
        + " "
        + System.getProperty("os.version")
        + " "
        + System.getProperty("os.arch")
        + "\nprocessors="
        + Runtime.getRuntime().availableProcessors()
        + "\nplantuml.sha256="
        + CorpusComparison.sha256(Files.readAllBytes(oracle))
        + "\nditaa.sha256="
        + CorpusComparison.sha256(Files.readAllBytes(svgOracle))
        + "\n"
    );
    boolean passed = true;
    for (int size : new int[] {1, 10, 50}) {
      StringBuilder diagram = new StringBuilder();
      for (int i = 0; i < size; i++) {
        diagram.append("+----------+\n| Service  |\n+----------+\n");
      }
      Path input = dir.resolve("input.ditaa");
      Files.writeString(input, diagram);
      for (String format : List.of("PNG", "SVG")) {
        long[][] timings = new long[2][30];
        long[][] rss = new long[2][30];
        for (int round = 0; round < 30; round++) {
          for (int order = 0; order < 2; order++) {
            int implementation = (round + order) % 2;
            String implementationName =
                implementation == 0 ? "traceur-ditaa" : format.equals("PNG") ? "plantuml" : "ditaa";
            Path rendered = dir.resolve(
                (implementation == 0 ? "actual." : "reference.") + format.toLowerCase(Locale.ROOT)
            );
            Files.deleteIfExists(rendered);
            List<String> command =
                new ArrayList<>(List.of(CorpusComparison.javaCommand(), "-Djava.awt.headless=true"));
            if (implementation == 0) {
              command.addAll(List.of(
                  "-cp",
                  System.getProperty("java.class.path"),
                  "RenderProcess",
                  input.toString(),
                  dir.resolve("actual." + format.toLowerCase(Locale.ROOT)).toString(),
                  format,
                  "1",
                  "true",
                  "true"
              ));
            } else if (format.equals("PNG")) {
              command.addAll(List.of(
                  "-cp",
                  System.getProperty("java.class.path") + File.pathSeparator + oracle,
                  "OraclePngProcess",
                  input.toString(),
                  rendered.toString(),
                  "1",
                  "true",
                  "true"
              ));
            } else {
              command.addAll(List.of(
                  "-jar",
                  svgOracle.toString(),
                  input.toString(),
                  dir.resolve("reference.svg").toString(),
                  "--svg",
                  "-o"
              ));
            }
            if (memory) {
              command.add(0, mac ? "-l" : "-v");
              command.add(0, "/usr/bin/time");
            }
            long start = System.nanoTime();
            Process process = new ProcessBuilder(command)
              .redirectErrorStream(true)
              .redirectOutput(dir.resolve("process.log").toFile())
              .start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
              process.destroyForcibly();
              process.waitFor();
              throw new IllegalStateException("Timed out: " + command);
            }
            timings[implementation][round] = System.nanoTime() - start;
            if (process.exitValue() != 0) {
              throw new IllegalStateException("Rendering failed; see "
                  + dir.resolve("process.log"));
            }
            if (!Files.isRegularFile(rendered) || Files.size(rendered) == 0) {
              throw new IllegalStateException("Missing output: " + rendered);
            }
            if (format.equals("PNG")) {
              if (javax.imageio.ImageIO.read(rendered.toFile()) == null) {
                throw new IllegalStateException("Invalid PNG: " + rendered);
              }
            } else {
              var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
              factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
              factory.newDocumentBuilder().parse(rendered.toFile());
            }
            if (memory) {
              var matcher = java.util.regex.Pattern
                .compile(
                    mac
                    ? "(?m)^\\s*(\\d+)\\s+maximum resident set size"
                    : "Maximum resident set size \\(kbytes\\):\\s*(\\d+)"
                )
                .matcher(Files.readString(dir.resolve("process.log")));
              if (!matcher.find()) {
                throw new IllegalStateException("Missing memory measurement");
              }
              rss[implementation][round] = Long.parseLong(matcher.group(1)) * (mac ? 1 : 1024);
            }
            samples
              .append(size)
              .append('\t')
              .append(format)
              .append('\t')
              .append(implementationName)
              .append('\t')
              .append(round)
              .append('\t')
              .append(timings[implementation][round])
              .append('\t')
              .append(rss[implementation][round])
              .append('\n');
          }
        }
        for (int i = 0; i < 2; i++) {
          Arrays.sort(timings[i]);
          Arrays.sort(rss[i]);
          results
            .append(size)
            .append('\t')
            .append(format)
            .append('\t')
            .append(i == 0 ? "traceur-ditaa" : format.equals("PNG") ? "plantuml" : "ditaa")
            .append('\t')
            .append((timings[i][14] + timings[i][15]) / 2_000_000.0)
            .append('\t')
            .append(timings[i][28] / 1_000_000.0)
            .append('\t')
            .append(memory ? (rss[i][14] + rss[i][15]) / 2.0 / 1048576 : "unavailable")
            .append('\t')
            .append(memory ? rss[i][28] / 1048576.0 : "unavailable")
            .append('\n');
        }
        passed &= timings[0][14] + timings[0][15] < timings[1][14] + timings[1][15]
            && timings[0][28] <= timings[1][28];
        Files.writeString(dir.resolve("results.tsv"), results);
        Files.writeString(dir.resolve("samples.tsv"), samples);
        System.out.println("Measured " + size + " boxes, " + format);
      }
    }
    System.out.print(results);
    if (!passed) {
      throw new AssertionError("Cold-start gate failed; see " + dir.resolve("results.tsv"));
    }
  }
}
