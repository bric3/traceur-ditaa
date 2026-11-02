/* SPDX-License-Identifier: MPL-2.0 */
import io.github.bric3.traceur.ditaa.DitaaRenderer;
import io.github.bric3.traceur.ditaa.OutputFormat;
import io.github.bric3.traceur.ditaa.RenderOptions;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Separate-process reference renderer comparisons; every failure is recorded and fails the task.
 */
public final class CorpusComparison {
  private final Path pngReferenceJar, svgReferenceJar, report;
  private final String rasterizer, rendererClasspath;
  private final StringBuilder rows = new StringBuilder(
      "input\tsha256\tvariant\tformat\tstatus\tdifferentPixels\treferenceDimensions"
      + "\tactualDimensions\tdetail\n"
  );
  private final StringBuilder html = new StringBuilder(
      "<!doctype html><meta charset=\"utf-8\"><title>Corpus comparison</title>"
      + "<style>body{font-family:sans-serif}img{max-width:45vw}section{border-top:1px solid #ccc;padding:1em}</style>"
      + "<h1>Corpus comparison</h1>"
  );
  private int passed, failed;

  CorpusComparison(
      Path pngReferenceJar,
      Path svgReferenceJar,
      Path report,
      String rasterizer,
      String rendererClasspath
  ) throws Exception {
    this.pngReferenceJar = pngReferenceJar;
    this.svgReferenceJar = svgReferenceJar;
    this.report = report;
    this.rasterizer = rasterizer;
    this.rendererClasspath = rendererClasspath;
    Files.createDirectories(report);
    Files.writeString(
        report.resolve("environment.txt"),
        "java="
        + System.getProperty("java.runtime.version")
        + "\njava.home="
        + System.getProperty("java.home")
        + "\nos="
        + System.getProperty("os.name")
        + " "
        + System.getProperty("os.version")
        + " "
        + System.getProperty("os.arch")
        + "\nplantuml.sha256="
        + sha256(Files.readAllBytes(pngReferenceJar))
        + "\nditaa.sha256="
        + sha256(Files.readAllBytes(svgReferenceJar))
        + "\nfonts="
        + String.join(
                ",",
                java.awt.GraphicsEnvironment
                  .getLocalGraphicsEnvironment()
                  .getAvailableFontFamilyNames(Locale.ROOT)
            )
        + "\n"
    );
    if (!run(
        List.of(rasterizer, "--version"),
        null,
        report.resolve("rasterizer.txt"),
        report.resolve("rasterizer-error.txt")
    )
      .equals("OK")) {
      throw new IllegalArgumentException("SVG rasterizer unavailable");
    }
    writeReport();
  }

  void compare(Path input, int variant, OutputFormat format) throws Exception {
    String id = sha256(Files.readAllBytes(input))
        + "-"
        + variant
        + "-"
        + format.name().toLowerCase(Locale.ROOT);
    Result result = compareCase(
        input,
        variant,
        format,
        pngReferenceJar,
        svgReferenceJar,
        rasterizer,
        report.resolve(id),
        rendererClasspath
    );
    record(result);
    if (!result.passed) {
      throw new AssertionError(result.row + "Report: " + report.resolve(id));
    }
  }

  private synchronized void record(Result result) throws IOException {
    rows.append(result.row);
    html.append(result.html);
    if (result.passed) {
      passed++;
    } else {
      failed++;
    }
    writeReport();
  }

  private void writeReport() throws IOException {
    Files.writeString(report.resolve("results.tsv"), rows);
    Files.writeString(
        report.resolve("index.html"),
        html + "<p>" + passed + " passed; " + failed + " failed</p>"
    );
  }

  private static Result compareCase(
      Path input,
      int variant,
      OutputFormat format,
      Path pngReferenceJar,
      Path svgReferenceJar,
      String rasterizer,
      Path dir,
      String rendererClasspath
  ) throws Exception {
    Files.createDirectories(dir);
    String status = "ERROR", detail = "", rd = "", ad = "", hash = "";
    long different = -1;
    try {
      String text = Files.readString(input);
      hash = sha256(text.getBytes(StandardCharsets.UTF_8));
      Path raw = dir.resolve("input.ditaa");
      Files.writeString(raw, text);
      RenderOptions options = new RenderOptions(variant == 3 ? .7 : 1, variant != 1, variant != 2);
      Path reference = dir.resolve("reference.png"), actual = dir.resolve("actual.png");
      if (format == OutputFormat.PNG) {
        status = run(
            List.of(
                javaCommand(),
                "-Djava.awt.headless=true",
                "-cp",
                rendererClasspath + java.io.File.pathSeparator + pngReferenceJar,
                PngReferenceProcess.class.getName(),
                raw.toString(),
                reference.toString(),
                Double.toString(options.scale()),
                Boolean.toString(options.shadows()),
                Boolean.toString(options.separation())
            ),
            null,
            dir.resolve("reference-stdout.log"),
            dir.resolve("reference.log")
        );
      } else {
        Path referenceSvg = dir.resolve("reference.svg");
        List<String> command = new ArrayList<>(List.of(
            javaCommand(),
            "-Djava.awt.headless=true",
            "-jar",
            svgReferenceJar.toString(),
            raw.toString(),
            referenceSvg.toString(),
            "--svg",
            "-o"
        ));
        if (variant == 1) {
          command.add("-S");
        }
        if (variant == 2) {
          command.add("-E");
        }
        if (variant == 3) {
          command.addAll(List.of("-s", "0.7"));
        }
        status = run(
            command,
            null,
            dir.resolve("reference.log"),
            dir.resolve("reference-error.log")
        );
        if (status.equals("OK")) {
          status = run(
              List.of(rasterizer, "--output", reference.toString(), referenceSvg.toString()),
              null,
              dir.resolve("rasterizer.log"),
              dir.resolve("rasterizer-error.log")
          );
        }
      }
      if (!status.equals("OK")) {
        status = "REFERENCE_" + status;
      } else {
        if (format == OutputFormat.PNG) {
          try (var output = Files.newOutputStream(actual)) {
            new DitaaRenderer().render(text, options, format, output);
          }
        } else {
          Path svg = dir.resolve("actual.svg");
          try (var output = Files.newOutputStream(svg)) {
            new DitaaRenderer().render(text, options, format, output);
          }
          var factory = DocumentBuilderFactory.newInstance();
          factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
          var document = factory.newDocumentBuilder().parse(svg.toFile());
          if (document.getElementsByTagName("image").getLength() != 0) {
            throw new IOException("SVG contains a raster image");
          }
          String rendering = run(
              List.of(rasterizer, "--output", actual.toString(), svg.toString()),
              null,
              dir.resolve("actual-rasterizer.log"),
              dir.resolve("actual-rasterizer-error.log")
          );
          if (!rendering.equals("OK")) {
            throw new IOException("SVG rasterisation " + rendering);
          }
        }
        BufferedImage expected = ImageIO.read(reference.toFile()), rendered =
            ImageIO.read(actual.toFile());
        if (expected == null || rendered == null) {
          throw new IOException("Invalid PNG output");
        }
        rd = expected.getWidth() + "x" + expected.getHeight();
        ad = rendered.getWidth() + "x" + rendered.getHeight();
        different = compare(expected, rendered, dir);
        status = rd.equals(ad) && different == 0 ? "PASS" : "DIFFERENT";
      }
    } catch (Exception e) {
      status = "ERROR";
      detail = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    String row = input.getFileName()
        + "\t"
        + hash
        + "\t"
        + variant
        + "\t"
        + format
        + "\t"
        + status
        + "\t"
        + different
        + "\t"
        + rd
        + "\t"
        + ad
        + "\t"
        + detail.replace('\n', ' ').replace('\t', ' ')
        + "\n";
    String id = dir.getFileName().toString();
    String html = "<section><h2>"
        + id
        + " "
        + status
        + "</h2><p>"
        + escape(input.getFileName().toString())
        + "; different pixels: "
        + different
        + "</p><p>Reference / Actual</p><img loading=\"lazy\" src=\""
        + id
        + "/reference.png\"><img loading=\"lazy\" src=\""
        + id
        + "/actual.png\"><p>Overlay / Difference</p><img loading=\"lazy\" src=\""
        + id
        + "/overlay.png\"><img loading=\"lazy\" src=\""
        + id
        + "/difference.png\"><p>"
        + escape(detail)
        + "</p></section>";
    return new Result(status.equals("PASS"), row, html);
  }

  static String run(List<String> command, Path input, Path output, Path error) throws Exception {
    var builder =
        new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(error.toFile());
    if (input != null) {
      builder.redirectInput(input.toFile());
    }
    Process process = builder.start();
    try {
      if (input == null) {
        process.getOutputStream().close();
      }
      if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor();
        return "TIMEOUT";
      }
      return process.exitValue() == 0 ? "OK" : "ERROR_EXIT_" + process.exitValue();
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  static String javaCommand() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  static long compare(BufferedImage reference, BufferedImage actual, Path dir) throws IOException {
    int w = Math.max(reference.getWidth(), actual.getWidth()), h =
        Math.max(reference.getHeight(), actual.getHeight());
    if ((long) w * h > 25_000_000) {
      throw new IOException("Reference image exceeds comparison size limit");
    }
    BufferedImage difference = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), overlay =
        new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = overlay.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, w, h);
      g.drawImage(reference, 0, 0, null);
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5f));
      g.drawImage(actual, 0, 0, null);
    } finally {
      g.dispose();
    }
    long count = 0;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        boolean inside = x < reference.getWidth()
            && y < reference.getHeight()
            && x < actual.getWidth()
            && y < actual.getHeight();
        boolean equal = inside && reference.getRGB(x, y) == actual.getRGB(x, y);
        if (!equal) {
          count++;
        }
        difference.setRGB(x, y, equal ? 0xffffff : 0xff00ff);
      }
    }
    ImageIO.write(difference, "png", dir.resolve("difference.png").toFile());
    ImageIO.write(overlay, "png", dir.resolve("overlay.png").toFile());
    return count;
  }

  static String sha256(byte[] bytes) throws Exception {
    StringBuilder hex = new StringBuilder();
    for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
      hex.append(String.format(Locale.ROOT, "%02x", b & 255));
    }
    return hex.toString();
  }

  private static String escape(String s) {
    return s
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }

  private static final class Result {
    final boolean passed;
    final String row, html;

    Result(boolean passed, String row, String html) {
      this.passed = passed;
      this.row = row;
      this.html = html;
    }
  }
}
