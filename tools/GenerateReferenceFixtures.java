// usr/bin/env jbang "$0" "$@" ; exit $?
// JAVA 25
// SPDX-License-Identifier: MPL-2.0

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Generates golden images from project-authored inputs using the isolated reference binary. */
class GenerateReferenceFixtures {
  record Options(double scale, boolean shadows, boolean separation) {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2 || args.length > 4)
      throw new IllegalArgumentException(
          "PLANTUML_JAR DITAA_JAR [JAVA11_EXECUTABLE|current [FIXTURE]]");
    boolean java11 = args.length >= 3 && !args[2].equals("current");
    String selected = args.length == 4 ? args[3] : null;
    String javaCommand =
        java11 ? args[2] : Path.of(System.getProperty("java.home"), "bin", "java").toString();
    Path oracle = Path.of(args[0]).toAbsolutePath();
    String hash =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(oracle)));
    if (!hash.equals("0f77e5f769836b3dee340e207fe497c3e4c43e973d559e3c306915da9c32e34c")) {
      throw new IllegalArgumentException("The reference binary does not match PlantUML 1.2026.8");
    }
    Path svgOracle = Path.of(args[1]).toAbsolutePath();
    String svgHash =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(svgOracle)));
    if (!svgHash.equals("9418aa63ff6d89c5d2318396f59836e120e75bea7a5930c4d34aa10fe7a196a9")) {
      throw new IllegalArgumentException("The reference binary does not match ditaa 0.11.0");
    }
    Path inputsDirectory = Path.of("src/test/resources/compatibility");
    Path fixtures =
        System.getProperty("os.name").equals("Linux")
            ? Files.createDirectories(inputsDirectory.resolve("linux"))
            : inputsDirectory;
    Path work =
        Files.createDirectories(
            Path.of(
                "build/reference-fixtures",
                System.getProperty("os.name"),
                java11 ? "java11" : "java25"));
    Path outputs = java11 ? Files.createDirectories(fixtures.resolve("java11")) : fixtures;
    String metadata =
        "# Project-authored inputs and reference renders use MPL-2.0.\n"
            + "oracle.version=PlantUML 1.2026.8\noracle.sha256="
            + hash
            + "\n"
            + "svg.version=ditaa 0.11.0\nsvg.sha256="
            + svgHash
            + "\n"
            + "java="
            + System.getProperty("java.runtime.version")
            + "\n"
            + "os="
            + System.getProperty("os.name")
            + " "
            + System.getProperty("os.arch")
            + "\n";
    if (java11) {
      Process version =
          new ProcessBuilder(javaCommand, "-version").redirectErrorStream(true).start();
      String actualVersion =
          new String(
              version.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      if (version.waitFor() != 0 || !actualVersion.contains("version \"11."))
        throw new IllegalArgumentException("Expected a Java 11 executable");
      metadata =
          "java="
              + actualVersion.lines().findFirst().orElseThrow()
              + "\nplantuml.sha256="
              + hash
              + "\n";
    }
    List<Path> inputs;
    try (var files = Files.list(inputsDirectory)) {
      inputs = files.filter(p -> p.toString().endsWith(".ditaa")).sorted().toList();
    }
    if (inputs.isEmpty()) throw new IllegalArgumentException("No authored fixtures found");
    if (selected != null) {
      if (inputs.stream()
          .noneMatch(input -> input.getFileName().toString().equals(selected + ".ditaa")))
        throw new IllegalArgumentException("No authored fixture named " + selected);
      Path provenance = outputs.resolve("reference.properties");
      if (!Files.isRegularFile(provenance) || !Files.readString(provenance).equals(metadata))
        throw new IllegalArgumentException(
            "Regenerate all fixtures when the reference environment changes");
    }
    List<Options> options = new ArrayList<>();
    for (double scale : new double[] {1, .7, 2}) {
      for (boolean shadows : new boolean[] {true, false}) {
        for (boolean separation : new boolean[] {true, false})
          options.add(new Options(scale, shadows, separation));
      }
    }
    List<String> cases = new ArrayList<>();
    int generated = 0;
    cases.add("name\tvariant\tscale\tshadows\tseparation");
    try (var executor = Executors.newFixedThreadPool(4)) {
      List<java.util.concurrent.Future<?>> jobs = new ArrayList<>();
      for (Path input : inputs) {
        String name = input.getFileName().toString().replace(".ditaa", "");
        for (int variant = 0; variant < options.size(); variant++) {
          int option = variant;
          Options settings = options.get(variant);
          cases.add(
              name
                  + "\t"
                  + variant
                  + "\t"
                  + settings.scale
                  + "\t"
                  + settings.shadows
                  + "\t"
                  + settings.separation);
          if (selected != null && !name.equals(selected)) continue;
          generated++;
          jobs.add(
              executor.submit(
                  () -> {
                    try {
                      String id = name + "-" + option;
                      Path wrapped = work.resolve(id + ".puml");
                      String flags =
                          (settings.shadows ? "" : " -S")
                              + (settings.separation ? "" : " -E")
                              + " scale="
                              + settings.scale;
                      Files.writeString(
                          wrapped,
                          "@startditaa" + flags + "\n" + Files.readString(input) + "\n@endditaa\n");
                      Path output = outputs.resolve(id + ".png");
                      Process process =
                          new ProcessBuilder(
                                  javaCommand,
                                  "-Djava.awt.headless=true",
                                  "-jar",
                                  oracle.toString(),
                                  "-nometadata",
                                  "-pipe")
                              .redirectInput(wrapped.toFile())
                              .redirectOutput(output.toFile())
                              .redirectError(work.resolve(id + ".log").toFile())
                              .start();
                      try {
                        if (!process.waitFor(20, TimeUnit.SECONDS)
                            || process.exitValue() != 0
                            || ImageIO.read(output.toFile()) == null) {
                          throw new IllegalStateException("Reference failed: " + id);
                        }
                      } finally {
                        if (process.isAlive()) {
                          process.destroyForcibly();
                          process.waitFor();
                        }
                      }
                      if (java11) return;
                      Path svg = fixtures.resolve(id + ".svg").toAbsolutePath();
                      var svgCommand =
                          new ArrayList<>(
                              List.of(
                                  Path.of(System.getProperty("java.home"), "bin", "java")
                                      .toString(),
                                  "-Djava.awt.headless=true",
                                  "-jar",
                                  svgOracle.toString(),
                                  input.toAbsolutePath().toString(),
                                  svg.toString(),
                                  "--svg",
                                  "-o",
                                  "-s",
                                  Double.toString(settings.scale)));
                      if (!settings.shadows) svgCommand.add("-S");
                      if (!settings.separation) svgCommand.add("-E");
                      Process svgProcess =
                          new ProcessBuilder(svgCommand)
                              .redirectErrorStream(true)
                              .redirectOutput(work.resolve(id + "-svg.log").toFile())
                              .start();
                      try {
                        if (!svgProcess.waitFor(20, TimeUnit.SECONDS)
                            || svgProcess.exitValue() != 0) {
                          throw new IllegalStateException("SVG reference failed: " + id);
                        }
                      } finally {
                        if (svgProcess.isAlive()) {
                          svgProcess.destroyForcibly();
                          svgProcess.waitFor();
                        }
                      }
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  }));
        }
      }
      for (var job : jobs) job.get();
    }
    Files.writeString(outputs.resolve("reference.properties"), metadata);
    if (java11) {
      System.out.println("Generated " + generated + " Java 11 PNG references");
      return;
    }
    Files.write(inputsDirectory.resolve("cases.tsv"), cases);
    System.out.println(
        "Generated " + generated + " PNG and SVG reference pairs from authored fixtures");
  }
}
