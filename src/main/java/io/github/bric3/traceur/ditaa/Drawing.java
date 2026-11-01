/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

final class Drawing {
  enum TextAlignment {
    LEFT,
    CENTRE,
    RIGHT
  }

  static final Font FONT = new Font(Font.DIALOG, Font.BOLD, 1).deriveFont(14.5f);
  private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, false);
  final int width;
  final int height;
  final double scale;
  final int cellWidth;
  final int cellHeight;
  final Font font;
  final boolean shadows;
  final List<Figure> figures = new ArrayList<>();
  final List<Label> labels = new ArrayList<>();

  Drawing(int width, int height, RenderOptions options) {
    this.width = width;
    this.height = height;
    this.scale = options.scale();
    this.cellWidth = (int) (10 * options.scale());
    this.cellHeight = (int) (14 * options.scale());
    if (cellHeight < 12) {
      this.font = FONT.deriveFont((float) Math.max(1, cellHeight - 1));
    } else {
      double ascentPerPoint = FONT.getLineMetrics("Z", FONT_CONTEXT).getAscent() / FONT.getSize2D();
      float size = (float) (Math.floor((cellHeight + .5) / ascentPerPoint * 2) / 2);
      this.font = FONT.deriveFont(size);
    }
    this.shadows = options.shadows();
  }

  static final class Figure {
    final Shape path;
    final Color fill;
    final boolean dashed;
    final boolean shadow;
    final boolean outline;

    Figure(Shape path, Color fill, boolean dashed, boolean shadow, boolean outline) {
      this.path = new Path2D.Float(path);
      this.fill = fill;
      this.dashed = dashed;
      this.shadow = shadow;
      this.outline = outline;
    }
  }

  static final class Label {
    final Font font;
    final String text;
    final double x;
    final double y;
    final Color colour;

    Label(
        String text,
        double x,
        double centreY,
        Color colour,
        int cellWidth,
        Font baseFont,
        TextAlignment alignment) {
      this.text = text;
      double available = text.length() * cellWidth;
      Font fitted = baseFont;
      while (fitted.getSize2D() > .5
          && fitted.getStringBounds(text, FONT_CONTEXT).getWidth() > available) {
        fitted = fitted.deriveFont(Math.max(.5f, (float) Math.ceil(fitted.getSize2D()) - 1));
      }
      this.font = fitted;
      double actualWidth = font.getStringBounds(text, FONT_CONTEXT).getWidth();
      double padding = available - actualWidth;
      this.x =
          x
              + (alignment == TextAlignment.LEFT
                  ? 0
                  : alignment == TextAlignment.RIGHT ? padding : Math.round(padding / 2));
      int capHeight =
          (int) Math.round(font.createGlyphVector(FONT_CONTEXT, "Z").getVisualBounds().getHeight());
      this.y = Math.round(centreY + capHeight / 2);
      this.colour = colour;
    }
  }
}
