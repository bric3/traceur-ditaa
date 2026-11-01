/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class SvgWriter {
  static void write(Drawing drawing, OutputStream output) throws IOException {
    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
        .append(drawing.width)
        .append("\" height=\"")
        .append(drawing.height)
        .append("\" viewBox=\"0 0 ")
        .append(drawing.width)
        .append(' ')
        .append(drawing.height)
        .append("\">\n")
        .append("<defs><filter id=\"shadow\" x=\"0\" y=\"0\" width=\"200%\" height=\"200%\">")
        .append("<feOffset in=\"SourceGraphic\" dx=\"5\" dy=\"5\" result=\"offset\"/>")
        .append("<feGaussianBlur in=\"offset\" stdDeviation=\"3\" result=\"blur\"/>")
        .append("<feBlend in=\"SourceGraphic\" in2=\"blur\" mode=\"normal\"/>")
        .append("</filter></defs>\n")
        .append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n")
        .append("<g transform=\"scale(")
        .append(number(drawing.scale))
        .append(' ')
        .append(number(drawing.scaleY))
        .append(
            ")\" stroke=\"black\" stroke-width=\"1\" stroke-linejoin=\"round\""
                + " stroke-linecap=\"round\">\n");
    for (Drawing.Figure figure : drawing.figures) {
      if (drawing.shadows && figure.shadow)
        svg.append("<path d=\"")
            .append(path(figure.path))
            .append("\" fill=\"gray\" stroke=\"gray\" filter=\"url(#shadow)\"/>\n");
    }
    for (Drawing.Figure figure : drawing.figures) {
      String path = path(figure.path);
      svg.append("<path d=\"")
          .append(path)
          .append("\" fill=\"")
          .append(figure.fill == null ? "none" : colour(figure.fill))
          .append('"');
      if (figure.dashed) svg.append(" stroke-dasharray=\"5 5\" stroke-linecap=\"butt\"");
      if (Color.BLACK.equals(figure.fill)) svg.append(" stroke=\"none\"");
      svg.append("/>\n");
    }
    svg.append(
        "<g stroke=\"none\" font-family=\"Courier\" font-size=\"15\""
            + " xml:space=\"preserve\">\n");
    for (Drawing.Label label : drawing.labels) {
      svg.append("<text x=\"")
          .append(number(label.x))
          .append("\" y=\"")
          .append(number(label.y))
          .append("\" fill=\"")
          .append(colour(label.colour))
          .append("\" font-size=\"")
          .append(label.font.getSize())
          .append("\">")
          .append(escape(label.text))
          .append("</text>\n");
    }
    svg.append("</g>\n</g>\n</svg>\n");
    output.write(svg.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static String path(Shape shape) {
    StringBuilder result = new StringBuilder();
    double[] coords = new double[6];
    for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
      int type = it.currentSegment(coords);
      int count;
      switch (type) {
        case PathIterator.SEG_MOVETO:
          result.append('M');
          count = 2;
          break;
        case PathIterator.SEG_LINETO:
          result.append('L');
          count = 2;
          break;
        case PathIterator.SEG_QUADTO:
          result.append('Q');
          count = 4;
          break;
        case PathIterator.SEG_CUBICTO:
          result.append('C');
          count = 6;
          break;
        case PathIterator.SEG_CLOSE:
          result.append('Z');
          count = 0;
          break;
        default:
          throw new IllegalStateException("Unknown path segment");
      }
      for (int i = 0; i < count; i++) result.append(i == 0 ? "" : " ").append(number(coords[i]));
    }
    return result.toString();
  }

  private static String colour(Color colour) {
    String hex = Integer.toHexString(colour.getRGB() & 0xffffff);
    return "#" + "000000".substring(hex.length()) + hex;
  }

  private static String number(double value) {
    return value == (long) value ? Long.toString((long) value) : Double.toString(value);
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
