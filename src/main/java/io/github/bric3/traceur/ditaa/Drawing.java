/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;

final class Drawing {
  static final Font FONT = new Font(Font.DIALOG, Font.BOLD, 1).deriveFont(14.5f);
  private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, false);
  final int width;
  final int height;
  final double scale;
  final double scaleY;
  final boolean shadows;
  final List<Figure> figures = new ArrayList<>();
  final List<Label> labels = new ArrayList<>();

  Drawing(int width, int height, RenderOptions options) {
    this.width = width;
    this.height = height;
    this.scale = (int) (10 * options.scale()) / 10.0;
    this.scaleY = (int) (14 * options.scale()) / 14.0;
    this.shadows = options.shadows();
  }

  static final class Figure {
    final Shape path;
    final Color fill;
    final boolean dashed;
    final boolean shadow;

    Figure(Shape path, Color fill, boolean dashed, boolean shadow) {
      this.path = path;
      this.fill = fill;
      this.dashed = dashed;
      this.shadow = shadow;
    }
  }

  static final class Label {
    final Font font;
    final String text;
    final double x;
    final double y;
    final Color colour;

    Label(String text, double x, double y, Color colour) {
      this.text = text;
      double available = text.length() * 10.0;
      double width = FONT.getStringBounds(text, FONT_CONTEXT).getWidth();
      this.font =
          width > available
              ? FONT.deriveFont((float) (FONT.getSize2D() * available / width))
              : FONT;
      double actualWidth = font.getStringBounds(text, FONT_CONTEXT).getWidth();
      this.x = x + (int) (available - actualWidth) / 2;
      this.y =
          y
              - (int)
                  ((FONT.getLineMetrics(text, FONT_CONTEXT).getAscent()
                          - font.getLineMetrics(text, FONT_CONTEXT).getAscent())
                      / 2);
      this.colour = colour;
    }
  }
}
