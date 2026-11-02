/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
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
  private Font font;
  final boolean shadows;
  final List<Figure> figures = new ArrayList<>();
  final List<Figure> shadowFigures = new ArrayList<>();
  final List<Label> labels = new ArrayList<>();

  Drawing(int width, int height, RenderOptions options) {
    this.width = width;
    this.height = height;
    this.scale = options.scale();
    this.cellWidth = (int) (10 * options.scale());
    this.cellHeight = (int) (14 * options.scale());
    this.shadows = options.shadows();
  }

  Font font() {
    if (font == null) font = fontFor(cellHeight);
    return font;
  }

  private static Font fontFor(int height) {
    // A 12-pixel cell retains the reference's 12.5-point default.
    if (height == 12) return FONT.deriveFont(12.5f);
    var canvas = new Canvas();
    // Small cells leave one ascent pixel free; larger cells use the full height.
    int limit = height < 12 ? height - 1 : height;
    double ascentPerPoint = FONT.getLineMetrics("Z", FONT_CONTEXT).getAscent() / FONT.getSize2D();
    float size = Math.max(1, (float) Math.floor(height / ascentPerPoint * 2) / 2);
    Font font = FONT.deriveFont(size);
    while (size > 1 && canvas.getFontMetrics(font).getAscent() > limit) {
      size -= .5f;
      font = FONT.deriveFont(size);
    }
    while (canvas.getFontMetrics(FONT.deriveFont(size + .5f)).getAscent() <= limit) {
      size += .5f;
      font = FONT.deriveFont(size);
    }
    return font;
  }

  static final class Figure {
    enum Kind {
      REGION,
      STORAGE,
      CONNECTOR,
      ARROW,
      MARKER
    }

    final Shape path;
    final Color fill;
    final boolean dashed;
    final boolean shadow;
    final Kind kind;
    final double area;

    Figure(Shape path, Color fill, boolean dashed, Kind kind, double area) {
      this.path = new Path2D.Float(path);
      this.fill = fill;
      this.dashed = dashed;
      this.shadow = (kind == Kind.REGION || kind == Kind.STORAGE) && !dashed;
      this.kind = kind;
      this.area = area;
    }
  }

  static final class Label {
    final Font font;
    final String text;
    final double x;
    final double y;
    private final Rectangle2D textBounds;
    Color colour;

    Label(
        String text,
        double x,
        double centreY,
        Color colour,
        int cellWidth,
        Font baseFont,
        TextAlignment alignment) {
      this.text = text;
      int available = text.length() * cellWidth;
      Font fitted = baseFont;
      Rectangle2D bounds = fitted.getStringBounds(text, FONT_CONTEXT);
      if (bounds.getWidth() > available) {
        fitted = fontForWidth(text, available);
        bounds = fitted.getStringBounds(text, FONT_CONTEXT);
      }
      this.font = fitted;
      this.textBounds = bounds;
      double actualWidth = bounds.getWidth();
      double padding = available - actualWidth;
      this.x =
          x
              + (alignment == TextAlignment.LEFT
                  ? 0
                  : alignment == TextAlignment.RIGHT
                      ? padding
                      : available / 2 - (int) actualWidth / 2);
      int capHeight =
          font.createGlyphVector(FONT_CONTEXT, "Z").getVisualBounds().getBounds().height;
      this.y = Math.round(centreY + capHeight / 2);
      this.colour = colour;
    }

    private static Font fontForWidth(String text, int width) {
      var canvas = new Canvas();
      Font base = FONT.deriveFont(12f);
      int baseWidth = canvas.getFontMetrics(base).stringWidth(text);
      // The reference leaves one pixel free when shrinking from its 12-point default.
      int limit = baseWidth > width ? width - 1 : width;
      float size = Math.max(1, (float) Math.floor(width * 12.0 / baseWidth * 2) / 2);
      Font font = base.deriveFont(size);
      while (size > 1 && canvas.getFontMetrics(font).stringWidth(text) > limit) {
        size -= .5f;
        font = base.deriveFont(size);
      }
      while (canvas.getFontMetrics(base.deriveFont(size + .5f)).stringWidth(text) <= limit) {
        size += .5f;
        font = base.deriveFont(size);
      }
      return font.deriveFont(Math.max(1, (float) Math.floor(size)));
    }

    Rectangle2D bounds() {
      return new Rectangle2D.Double(
          x + textBounds.getX(), y + textBounds.getY(), textBounds.getWidth(), textBounds.getHeight());
    }
  }
}
