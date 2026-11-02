/* SPDX-License-Identifier: MPL-2.0 */
import java.awt.image.RenderedImage;
import io.github.bric3.traceur.ditaa.testing.ReferenceFonts;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Isolated reference entry point: invokes public binary APIs and preserves raw input whitespace.
 */
public final class PngReferenceProcess {
  public static void main(String[] args) throws Exception {
    if (args.length != 5) {
      throw new IllegalArgumentException("INPUT OUTPUT SCALE SHADOWS SEPARATION");
    }
    String prefix = "org.stathissideris.ascii2image.";
    Class<?> conversionType = Class.forName(prefix + "core.ConversionOptions");
    Object conversion = conversionType.getConstructor().newInstance();
    Object processing = conversionType.getField("processingOptions").get(conversion);
    Object rendering = conversionType.getField("renderingOptions").get(conversion);
    rendering
      .getClass()
      .getMethod("setScale", float.class)
      .invoke(rendering, Float.parseFloat(args[2]));
    rendering
      .getClass()
      .getMethod("setDropShadows", boolean.class)
      .invoke(rendering, Boolean.parseBoolean(args[3]));
    processing
      .getClass()
      .getMethod("setPerformSeparationOfCommonEdges", boolean.class)
      .invoke(processing, Boolean.parseBoolean(args[4]));

    if (Boolean.getBoolean("traceur.reference.warmFonts") && ReferenceFonts.requiresWarmup()) {
      render(ReferenceFonts.PRIMER, conversion);
    }
    RenderedImage image = render(Files.readString(Path.of(args[0])), conversion);
    if (!ImageIO.write(image, "png", Path.of(args[1]).toFile())) {
      throw new IllegalStateException("No PNG encoder available");
    }
  }

  private static RenderedImage render(String text, Object conversion) throws Exception {
    String prefix = "org.stathissideris.ascii2image.";
    Object processing = conversion.getClass().getField("processingOptions").get(conversion);
    Object rendering = conversion.getClass().getField("renderingOptions").get(conversion);
    Class<?> gridType = Class.forName(prefix + "text.TextGrid");
    Object grid = gridType.getConstructor().newInstance();
    gridType
      .getMethod("initialiseWithText", String.class, processing.getClass())
      .invoke(grid, text, processing);
    Class<?> diagramType = Class.forName(prefix + "graphics.Diagram");
    Object diagram =
        diagramType.getConstructor(gridType, conversion.getClass()).newInstance(grid, conversion);
    Class<?> rendererType = Class.forName(prefix + "graphics.BitmapRenderer");
    return (RenderedImage) rendererType
      .getMethod("renderToImage", diagramType, rendering.getClass())
      .invoke(rendererType.getConstructor().newInstance(), diagram, rendering);
  }
}
