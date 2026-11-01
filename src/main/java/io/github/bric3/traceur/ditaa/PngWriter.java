/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;

final class PngWriter {
  static void write(Drawing drawing, OutputStream output) throws IOException {
    BufferedImage image =
        new BufferedImage(drawing.width, drawing.height, BufferedImage.TYPE_INT_RGB);
    if (drawing.shadows) {
      Graphics2D shadow = image.createGraphics();
      try {
        shadow.setColor(Color.WHITE);
        shadow.fillRect(0, 0, drawing.width, drawing.height);
        shadow.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        shadow.setColor(new Color(150, 150, 150));
        for (Drawing.Figure figure : drawing.figures)
          if (figure.shadow) {
            Path2D.Float shifted = new Path2D.Float(figure.path);
            // The reference displaces shadows by this fraction of a character cell.
            double offset = drawing.cellWidth / 3.333;
            shifted.transform(AffineTransform.getTranslateInstance(offset, offset));
            shadow.fill(shifted);
          }
      } finally {
        shadow.dispose();
      }
      float[] kernel = new float[36];
      Arrays.fill(kernel, 1f / 36);
      image =
          new ConvolveOp(new Kernel(6, 6, kernel), ConvolveOp.EDGE_NO_OP, null).filter(image, null);
    }
    Graphics2D graphics = image.createGraphics();
    try {
      if (!drawing.shadows) {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, drawing.width, drawing.height);
      }
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      for (Drawing.Figure figure : drawing.figures) {
        graphics.setStroke(
            figure.dashed
                ? new BasicStroke(
                    (float) drawing.scale,
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_ROUND,
                    10,
                    new float[] {
                      Math.max(1, (int) (5 * drawing.scale)), Math.max(1, (int) (5 * drawing.scale))
                    },
                    0)
                : new BasicStroke(
                    (float) drawing.scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (figure.fill != null && !figure.dashed) {
          graphics.setColor(figure.fill);
          graphics.fill(figure.path);
        }
        graphics.setColor(Color.BLACK);
        if (figure.outline) graphics.draw(figure.path);
      }
      for (Drawing.Label label : drawing.labels) {
        graphics.setFont(label.font);
        graphics.setColor(label.colour);
        graphics.drawString(label.text, (float) label.x, (float) label.y);
      }
    } finally {
      graphics.dispose();
    }
    if (!ImageIO.write(image, "png", output)) throw new IOException("No PNG encoder available");
  }
}
