/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiagramParser {
  private static final int MAX_CELLS = 250_000;
  private static final long MAX_PIXELS = 25_000_000;
  private static final Pattern MARKUP =
      Pattern.compile("c([0-9a-fA-F]{3}|RED|BLU|GRE|PNK|BLK|YEL)|\\{(d|s|io|o|mo|c|tr)\\}");
  private static final int[] DX = {1, 0, -1, 0};
  private static final int[] DY = {0, 1, 0, -1};

  private final int width;
  private final int height;
  private final char[][] grid;
  private final int[][] links;
  private final boolean[][] consumed;
  private final RenderOptions options;

  private DiagramParser(List<String> lines, int width, RenderOptions options) {
    this.width = width;
    this.height = lines.size();
    this.options = options;
    grid = new char[height][width];
    links = new int[height][width];
    consumed = new boolean[height][width];
    for (int y = 0; y < height; y++) {
      Arrays.fill(grid[y], ' ');
      lines.get(y).getChars(0, lines.get(y).length(), grid[y], 0);
    }
  }

  static Drawing parse(String text, RenderOptions options) {
    if (text.length() > MAX_CELLS * 4) {
      throw new IllegalArgumentException("Diagram exceeds input size limit");
    }
    List<String> lines = new ArrayList<>();
    int width = 0;
    for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
      StringBuilder expanded = new StringBuilder();
      for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (c == '\t') {
          int spaces = 8 - expanded.length() % 8;
          expanded.append("        ", 0, spaces);
        } else {
          if (Character.isISOControl(c)
              || c == 0xfffe
              || c == 0xffff
              || (Character.isSurrogate(c)
                  && !(Character.isHighSurrogate(c)
                      && i + 1 < line.length()
                      && Character.isLowSurrogate(line.charAt(i + 1))))) {
            throw new IllegalArgumentException("Diagram contains an invalid text character");
          }
          expanded.append(c);
          if (Character.isHighSurrogate(c)) expanded.append(line.charAt(++i));
        }
      }
      width = Math.max(width, expanded.length());
      lines.add(expanded.toString());
    }
    while (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty())
      lines.remove(lines.size() - 1);
    if ((long) Math.max(width, 1) * lines.size() > MAX_CELLS) {
      throw new IllegalArgumentException("Diagram exceeds cell limit");
    }
    double pixelWidth = (width + 4.0) * (int) (10 * options.scale());
    double pixelHeight = (lines.size() + 4.0) * (int) (14 * options.scale());
    if (pixelWidth > 32768
        || pixelHeight > 32768
        || pixelWidth * pixelHeight > MAX_PIXELS
        || pixelWidth < 1
        || pixelHeight < 1) {
      throw new IllegalArgumentException("Rendered dimensions exceed image limits");
    }
    Drawing drawing = new Drawing((int) pixelWidth, (int) pixelHeight, options);
    new DiagramParser(lines, width, options).build(drawing);
    return drawing;
  }

  private char at(int x, int y) {
    return x < 0 || y < 0 || x >= width || y >= height ? ' ' : grid[y][x];
  }

  private int directions(int x, int y) {
    switch (at(x, y)) {
      case '-':
      case '=':
        return 5;
      case '|':
      case ':':
        return 10;
      case '+':
      case '/':
      case '\\':
      case '*':
        return 15;
      case '>':
        return 4;
      case '<':
        return 1;
      case '^':
        return 2;
      case 'v':
        return 8;
      default:
        return 0;
    }
  }

  private void build(Drawing drawing) {
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        for (int d = 0; d < 4; d++) {
          if ((directions(x, y) & (1 << d)) != 0
              && (directions(x + DX[d], y + DY[d]) & (1 << ((d + 2) % 4))) != 0) {
            links[y][x] |= 1 << d;
          }
        }
      }
    // A half-edge walk keeps the bounded regions separate when boxes share boundaries.
    boolean[][][] visited = new boolean[height][width][4];
    List<Region> regions = new ArrayList<>();
    Set<Long> boundary = new HashSet<>();
    Set<Long> sharedEdges = new HashSet<>();
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        for (int d = 0; d < 4; d++) {
          if ((links[y][x] & (1 << d)) == 0 || visited[y][x][d]) continue;
          List<Point2D.Double> points = new ArrayList<>();
          List<Long> edges = new ArrayList<>();
          int px = x, py = y, direction = d;
          do {
            visited[py][px][direction] = true;
            points.add(new Point2D.Double(px, py));
            int nx = px + DX[direction], ny = py + DY[direction];
            edges.add(edge(px, py, nx, ny));
            int reverse = (direction + 2) % 4;
            int next = reverse;
            for (int turn = 1; turn <= 4; turn++) {
              int candidate = (reverse + 4 - turn) % 4;
              if ((links[ny][nx] & (1 << candidate)) != 0) {
                next = candidate;
                break;
              }
            }
            px = nx;
            py = ny;
            direction = next;
          } while (!(px == x && py == y && direction == d));
          double area = 0;
          for (int i = 0; i < points.size(); i++) {
            Point2D.Double a = points.get(i), b = points.get((i + 1) % points.size());
            area += a.x * b.y - b.x * a.y;
          }
          if (area > 0) {
            // A bridge is visited in both directions; it remains an independent connector.
            Set<Long> faceEdges = new HashSet<>();
            Set<Long> bridges = new HashSet<>();
            for (long edge : edges) if (!faceEdges.add(edge)) bridges.add(edge);
            faceEdges.removeAll(bridges);
            boolean dashed = false;
            for (long edge : faceEdges) {
              int a = (int) (edge >>> 32), b = (int) edge;
              char start = at(a % width, a / width), end = at(b % width, b / width);
              dashed |= start == '=' || start == ':' || end == '=' || end == ':';
            }
            Region region = new Region(points, area, dashed);
            regions.add(region);
            for (long edge : faceEdges) {
              if (!boundary.add(edge)) sharedEdges.add(edge);
            }
          }
        }
      }
    regions.sort(Comparator.comparingDouble((Region r) -> r.area).reversed());
    for (int y = 0; y < height; y++) {
      Matcher matcher = MARKUP.matcher(new String(grid[y]));
      while (matcher.find()) {
        Region owner = null;
        for (Region region : regions)
          if (region.gridPath.contains(matcher.start(), y)) owner = region;
        if (owner == null) continue;
        if (matcher.group(1) != null) owner.colour = colour(matcher.group(1));
        else owner.tag = matcher.group(2);
        Arrays.fill(consumed[y], matcher.start(), matcher.end(), true);
      }
    }
    for (Region region : regions) {
      region.shape = shape(region, sharedEdges);
      drawing.figures.add(new Drawing.Figure(region.shape, region.colour, region.dashed, true));
    }

    // Draw each remaining connector as a continuous path so dash phase spans cells.
    Set<Long> drawn = new HashSet<>(boundary);
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        if (Integer.bitCount(links[y][x]) != 2) connectors(x, y, drawn, drawing);
      }
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        connectors(x, y, drawn, drawing);
        if (links[y][x] == 0) continue;
        consumed[y][x] = true;
        char c = at(x, y);
        if ("<>^v".indexOf(c) >= 0) {
          double cx = cx(x), cy = cy(y);
          int d = " >v<^".indexOf(c) - 1;
          Path2D.Double arrow = new Path2D.Double();
          arrow.moveTo(cx + DX[d] * 5, cy + DY[d] * 7);
          arrow.lineTo(cx - DX[d] * 5 + DY[d] * 5, cy - DY[d] * 7 - DX[d] * 7);
          arrow.lineTo(cx - DX[d] * 5 - DY[d] * 5, cy - DY[d] * 7 + DX[d] * 7);
          arrow.closePath();
          drawing.figures.add(new Drawing.Figure(arrow, Color.BLACK, false, false));
        } else if (c == '*') {
          drawing.figures.add(
              new Drawing.Figure(
                  new Ellipse2D.Double(cx(x) - 3, cy(y) - 3, 6, 6), Color.WHITE, false, false));
        }
      }

    for (int y = 0; y < height; y++) {
      int x = 0;
      while (x < width) {
        if (consumed[y][x] || at(x, y) == ' ') {
          x++;
          continue;
        }
        int start = x++;
        while (x < width && !consumed[y][x] && !(at(x, y) == ' ' && at(x + 1, y) == ' ')) x++;
        String label = new String(grid[y], start, x - start).stripTrailing();
        if (label.isEmpty()) continue;
        if (start > 0 && at(start - 1, y) == ' ' && label.startsWith("o "))
          label = "\u2022" + label.substring(1);
        Color colour = Color.BLACK;
        for (Region region : regions)
          if (region.gridPath.contains(start, y)) {
            Color bg = region.colour;
            colour =
                bg.getRed() * 299 + bg.getGreen() * 587 + bg.getBlue() * 114 < 100000
                    ? Color.WHITE
                    : Color.BLACK;
          }
        drawing.labels.add(new Drawing.Label(label, (start + 2) * 10, (y + 2) * 14 + 12, colour));
      }
    }
  }

  private void connectors(int x, int y, Set<Long> drawn, Drawing drawing) {
    for (int direction = 0; direction < 4; direction++) {
      if ((links[y][x] & (1 << direction)) == 0) continue;
      int nx = x + DX[direction], ny = y + DY[direction];
      if (drawn.contains(edge(x, y, nx, ny))) continue;
      Path2D.Double path = new Path2D.Double();
      boolean tail = Integer.bitCount(links[y][x]) == 1 && "-=|:".indexOf(at(x, y)) >= 0;
      path.moveTo(cx(x) - (tail ? DX[direction] * 5 : 0), cy(y) - (tail ? DY[direction] * 7 : 0));
      int px = x, py = y, d = direction;
      boolean dashed = at(px, py) == '=' || at(px, py) == ':';
      do {
        nx = px + DX[d];
        ny = py + DY[d];
        if (!drawn.add(edge(px, py, nx, ny))) break;
        boolean end = Integer.bitCount(links[ny][nx]) == 1 && "-=|:".indexOf(at(nx, ny)) >= 0;
        path.lineTo(cx(nx) + (end ? DX[d] * 5 : 0), cy(ny) + (end ? DY[d] * 7 : 0));
        dashed |= at(nx, ny) == '=' || at(nx, ny) == ':';
        if (Integer.bitCount(links[ny][nx]) != 2) break;
        int remaining = links[ny][nx] & ~(1 << ((d + 2) % 4));
        px = nx;
        py = ny;
        d = Integer.numberOfTrailingZeros(remaining);
      } while (true);
      drawing.figures.add(new Drawing.Figure(path, null, dashed, false));
    }
  }

  private Shape shape(Region region, Set<Long> sharedEdges) {
    List<Integer> corners = new ArrayList<>();
    List<Point2D.Double> p = region.points;
    for (int i = 0; i < p.size(); i++) {
      Point2D.Double a = p.get((i + p.size() - 1) % p.size()),
          b = p.get(i),
          c = p.get((i + 1) % p.size());
      if ((b.x - a.x) * (c.y - b.y) != (b.y - a.y) * (c.x - b.x)) corners.add(i);
    }
    Path2D.Double path = new Path2D.Double();
    for (int i = 0; i < corners.size(); i++) {
      Point2D.Double previous = p.get(corners.get((i + corners.size() - 1) % corners.size()));
      int pointIndex = corners.get(i);
      Point2D.Double point = p.get(pointIndex), next = p.get(corners.get((i + 1) % corners.size()));
      double x = cx(point.x), y = cy(point.y);
      if (options.separation()) {
        Point2D.Double before = p.get((pointIndex + p.size() - 1) % p.size());
        Point2D.Double after = p.get((pointIndex + 1) % p.size());
        if (sharedEdges.contains(
            edge((int) before.x, (int) before.y, (int) point.x, (int) point.y))) {
          x -= Math.signum(point.y - before.y) * 2;
          y += Math.signum(point.x - before.x) * 2;
        }
        if (sharedEdges.contains(
            edge((int) point.x, (int) point.y, (int) after.x, (int) after.y))) {
          x -= Math.signum(after.y - point.y) * 2;
          y += Math.signum(after.x - point.x) * 2;
        }
      }
      boolean rounded =
          at((int) point.x, (int) point.y) == '/' || at((int) point.x, (int) point.y) == '\\';
      double r = rounded ? 5 : 0;
      double ix = x + Math.signum(previous.x - point.x) * r,
          iy = y + Math.signum(previous.y - point.y) * r;
      if (i == 0) path.moveTo(ix, iy);
      else path.lineTo(ix, iy);
      if (rounded)
        path.quadTo(
            x, y, x + Math.signum(next.x - point.x) * r, y + Math.signum(next.y - point.y) * r);
    }
    path.closePath();
    Rectangle2D bounds = path.getBounds2D();
    double x = bounds.getX(), y = bounds.getY(), w = bounds.getWidth(), h = bounds.getHeight();
    if (region.tag.isEmpty()) return path;
    if (region.tag.equals("o")) return new Ellipse2D.Double(x, y, w, h);
    path = new Path2D.Double();
    switch (region.tag) {
      case "c":
        path.moveTo(x + w / 2, y);
        path.lineTo(x + w, y + h / 2);
        path.lineTo(x + w / 2, y + h);
        path.lineTo(x, y + h / 2);
        break;
      case "io":
        path.moveTo(x + 5, y);
        path.lineTo(x + w + 5, y);
        path.lineTo(x + w - 5, y + h);
        path.lineTo(x - 5, y + h);
        break;
      case "mo":
        path.moveTo(x - 5, y);
        path.lineTo(x + w + 5, y);
        path.lineTo(x + w - 5, y + h);
        path.lineTo(x + 5, y + h);
        break;
      case "tr":
        path.moveTo(x + 5, y);
        path.lineTo(x + w - 5, y);
        path.lineTo(x + w + 5, y + h);
        path.lineTo(x - 5, y + h);
        break;
      case "d":
        path.moveTo(x, y);
        path.lineTo(x + w, y);
        path.lineTo(x + w, y + h);
        path.quadTo(x + w * 2 / 3, y + h - 5, x + w / 2, y + h);
        path.quadTo(x + w / 3, y + h + 5, x, y + h);
        break;
      case "s":
        path.moveTo(x, y);
        path.curveTo(x + w / 6, y + 7, x + w * 5 / 6, y + 7, x + w, y);
        path.curveTo(x + w * 5 / 6, y - 7, x + w / 6, y - 7, x, y);
        path.moveTo(x, y);
        path.lineTo(x, y + h);
        path.curveTo(x + w / 6, y + h + 10, x + w * 5 / 6, y + h + 10, x + w, y + h);
        path.lineTo(x + w, y);
        return path;
      default:
        throw new IllegalStateException("Unknown shape tag");
    }
    path.closePath();
    return path;
  }

  private long edge(int x, int y, int nx, int ny) {
    int a = y * width + x, b = ny * width + nx;
    return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xffffffffL);
  }

  private static double cx(double x) {
    return (x + 2) * 10 + 5;
  }

  private static double cy(double y) {
    return (y + 2) * 14 + 7;
  }

  private static Color colour(String code) {
    switch (code) {
      case "RED":
        return new Color(0xee3322);
      case "BLU":
        return new Color(0x5555bb);
      case "GRE":
        return new Color(0x99dd99);
      case "PNK":
        return new Color(0xffaaaa);
      case "BLK":
        return Color.BLACK;
      case "YEL":
        return new Color(0xffff33);
      default:
        int rgb = Integer.parseInt(code, 16);
        return new Color(((rgb >> 8) & 15) * 17, ((rgb >> 4) & 15) * 17, (rgb & 15) * 17);
    }
  }

  private static final class Region {
    final List<Point2D.Double> points;
    final Path2D.Double gridPath = new Path2D.Double();
    final double area;
    final boolean dashed;
    Color colour = Color.WHITE;
    String tag = "";
    Shape shape;

    Region(List<Point2D.Double> points, double area, boolean dashed) {
      this.points = points;
      this.area = area;
      this.dashed = dashed;
      gridPath.moveTo(points.get(0).x, points.get(0).y);
      for (int i = 1; i < points.size(); i++) gridPath.lineTo(points.get(i).x, points.get(i).y);
      gridPath.closePath();
    }
  }
}
