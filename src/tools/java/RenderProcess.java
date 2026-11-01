/* SPDX-License-Identifier: MPL-2.0 */
import io.github.bric3.traceur.ditaa.DitaaRenderer;
import io.github.bric3.traceur.ditaa.OutputFormat;
import io.github.bric3.traceur.ditaa.RenderOptions;
import java.nio.file.Files;
import java.nio.file.Path;

/** Internal subprocess entry point for comparisons and cold-start measurements. */
public final class RenderProcess {
  public static void main(String[] args) throws Exception {
    if (args.length != 6)
      throw new IllegalArgumentException("INPUT OUTPUT FORMAT SCALE SHADOWS SEPARATION");
    try (var output = Files.newOutputStream(Path.of(args[1]))) {
      new DitaaRenderer()
          .render(
              Files.readString(Path.of(args[0])),
              new RenderOptions(
                  Double.parseDouble(args[3]),
                  Boolean.parseBoolean(args[4]),
                  Boolean.parseBoolean(args[5])),
              OutputFormat.valueOf(args[2]),
              output);
    }
  }
}
