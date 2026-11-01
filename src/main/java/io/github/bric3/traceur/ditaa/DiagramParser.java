/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiagramParser {
  private static final int MAX_CELLS = 250_000;
  private static final long MAX_PIXELS = 25_000_000;
  private static final Pattern MARKUP =
      Pattern.compile("c([0-9A-F]{3}|RED|BLU|GRE|PNK|BLK|YEL)|\\{(d|s|io|o|mo|c|tr)\\}");
  private static final int[] DX = {1, 0, -1, 0};
  private static final int[] DY = {0, 1, 0, -1};

  private final int width;
  private final int height;
  private final char[][] grid;
  private final int[][] links;
  private final boolean[][] consumed;
  private final RenderOptions options;
  private final int cellWidth;
  private final int cellHeight;

  private DiagramParser(List<String> lines, int width, RenderOptions options) {
    this.width = width;
    this.height = lines.size();
    this.options = options;
    this.cellWidth = (int) (10 * options.scale());
    this.cellHeight = (int) (14 * options.scale());
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
        || pixelWidth < 1) {
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
    char c = at(x, y);
    if (c != '/' && c != '\\') return basicDirections(x, y);
    int neighbours = 0, lines = 0;
    for (int direction = 0; direction < 4; direction++) {
      if ((basicDirections(x + DX[direction], y + DY[direction]) & (1 << ((direction + 2) % 4)))
          != 0) {
        neighbours |= 1 << direction;
        char neighbour = at(x + DX[direction], y + DY[direction]);
        if (neighbour != '/' && neighbour != '\\') lines |= 1 << direction;
      }
    }
    int first = c == '/' ? 3 : 6;
    int second = c == '/' ? 12 : 9;
    if ((neighbours & first) == first
        && ((neighbours & second) != second
            || Integer.bitCount(lines & first) >= Integer.bitCount(lines & second))) return first;
    if ((neighbours & second) == second) return second;
    return 0;
  }

  private int basicDirections(int x, int y) {
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
      case '<':
      case '^':
        return arrowDirections(x, y);
      case 'v':
      case 'V':
        return Character.isLetterOrDigit(at(x - 1, y)) || Character.isLetterOrDigit(at(x + 1, y))
            ? 0
            : arrowDirections(x, y);
      default:
        return 0;
    }
  }

  private int arrowDirections(int x, int y) {
    int directions = 0;
    for (int d = 0; d < 4; d++) {
      if ("-=|:".indexOf(at(x + DX[d], y + DY[d])) >= 0) directions |= 1 << d;
    }
    return directions;
  }

  private void build(Drawing drawing) {
    Drawing.Figure[][] arrowCells = new Drawing.Figure[height][width];
    for (int y = 0; y < height; y++)
      for (int x = 0; x + 2 < width; x++) {
        char marker = at(x, y);
        if ((marker == 'o' || marker == '*')
            && (x == 0 || at(x - 1, y) == ' ')
            && at(x + 1, y) == ' '
            && at(x + 2, y) != ' ') {
          grid[y][x] = ' ';
          grid[y][x + 1] = '\u2022';
        }
      }
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
        Arrays.fill(consumed[y], matcher.start(), matcher.end(), true);
        Region owner = null;
        for (Region region : regions)
          if (region.gridPath.contains(matcher.start(), y)) owner = region;
        if (owner == null) continue;
        if (matcher.group(1) != null) owner.colour = colour(matcher.group(1));
        else owner.tag = matcher.group(2);
      }
    }
    for (Region region : regions) {
      region.shape = shape(region, sharedEdges);
    }
    regions.sort(Comparator.comparingDouble((Region r) -> r.renderArea).reversed());
    for (Region region : regions) {
      drawing.figures.add(
          new Drawing.Figure(
              region.shape,
              region.colour,
              region.dashed,
              region.tag.equals("s") ? Drawing.Figure.Kind.STORAGE : Drawing.Figure.Kind.REGION,
              region.renderArea));
    }

    // Draw each remaining connector as a continuous path so dash phase spans cells.
    Set<Long> drawn = new HashSet<>(boundary);
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        if (Integer.bitCount(links[y][x]) != 2 || "<>^vV".indexOf(at(x, y)) >= 0)
          connectors(x, y, boundary, drawn, drawing);
      }
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        connectors(x, y, boundary, drawn, drawing);
        char c = at(x, y);
        boolean arrowCharacter =
            "<>^".indexOf(c) >= 0 || (c == 'v' || c == 'V') && links[y][x] != 0;
        boolean loneLine = "-=|:".indexOf(c) >= 0;
        if (links[y][x] == 0 && !arrowCharacter && !loneLine && c != '*') continue;
        consumed[y][x] = true;
        if (arrowCharacter) {
          double cx = cx(x), cy = cy(y);
          int d = " >v<^".indexOf(Character.toLowerCase(c)) - 1;
          Path2D.Double arrow = new Path2D.Double();
          double left = (x + 2) * cellWidth, top = (y + 2) * cellHeight;
          double right = left + cellWidth, bottom = top + cellHeight;
          switch (d) {
            case 0:
              arrow.moveTo(right, cy);
              arrow.lineTo(left, top);
              arrow.lineTo(left, bottom);
              break;
            case 1:
              arrow.moveTo(cx, bottom);
              arrow.lineTo(right, top);
              arrow.lineTo(left, top);
              break;
            case 2:
              arrow.moveTo(left, cy);
              arrow.lineTo(right, bottom);
              arrow.lineTo(right, top);
              break;
            default:
              arrow.moveTo(cx, top);
              arrow.lineTo(left, bottom);
              arrow.lineTo(right, bottom);
              break;
          }
          arrow.closePath();
          Drawing.Figure arrowFigure =
              new Drawing.Figure(
                  arrow,
                  Color.BLACK,
                  false,
                  Drawing.Figure.Kind.ARROW,
                  cellWidth * cellHeight / 2.0);
          drawing.figures.add(arrowFigure);
          arrowCells[y][x] = arrowFigure;
        } else if (c == '*') {
          if (links[y][x] != 0) {
            drawing.figures.add(
                new Drawing.Figure(
                    new Ellipse2D.Double(
                        cx(x) - cellWidth * .35,
                        cy(y) - cellWidth * .35,
                        cellWidth * .7,
                        cellWidth * .7),
                    Color.WHITE,
                    false,
                    Drawing.Figure.Kind.MARKER,
                    0));
          }
        } else if (links[y][x] == 0 && (c == '-' || c == '|')) {
          Path2D.Double path = new Path2D.Double();
          if (c == '-') {
            path.moveTo((x + 2) * cellWidth, cy(y));
            path.lineTo((x + 3) * cellWidth - 1, cy(y));
          } else {
            path.moveTo(cx(x), (y + 2) * cellHeight);
            path.lineTo(cx(x), (y + 3) * cellHeight - 1);
          }
          drawing.figures.add(
              new Drawing.Figure(path, null, false, Drawing.Figure.Kind.CONNECTOR, 0));
        }
      }

    drawing.figures.sort(
        Comparator.comparing((Drawing.Figure f) -> f.kind != Drawing.Figure.Kind.STORAGE)
            .thenComparing(f -> f.kind == Drawing.Figure.Kind.MARKER)
            .thenComparing(Comparator.comparingDouble((Drawing.Figure f) -> f.area).reversed()));

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) if (consumed[y][x]) grid[y][x] = ' ';
    }
    int[][] groups = textGroups();
    Map<Long, Integer> starts = new HashMap<>(), ends = new HashMap<>();
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        long key = ((long) groups[y][x] << 32) | x;
        if (textStartsAt(x, y)) starts.merge(key, 1, Integer::sum);
        if (textEndsAt(x, y)) ends.merge(key, 1, Integer::sum);
      }
    for (int y = 0; y < height; y++) {
      int x = 0;
      while (x < width) {
        if (at(x, y) == ' ') {
          x++;
          continue;
        }
        int start = x++;
        while (x < width && !(at(x, y) == ' ' && at(x + 1, y) == ' ')) x++;
        String label = new String(grid[y], start, x - start).stripTrailing();
        if (label.length() == 1 && at(x, y) == ' ') label += " ";
        Color colour = Color.BLACK;
        for (Region region : regions)
          if (region.gridPath.contains(start, y)) {
            Color bg = region.colour;
            // The reference uses the brightest RGB channel for text foreground.
            colour =
                Math.max(bg.getRed(), Math.max(bg.getGreen(), bg.getBlue())) < 200
                    ? Color.WHITE
                    : Color.BLACK;
          }
        int end = start + label.length() - 1;
        long group = (long) groups[y][start] << 32;
        int otherStarts = starts.getOrDefault(group | start, 0) - 1;
        int otherEnds = ends.getOrDefault(group | end, 0) - (textEndsAt(end, y) ? 1 : 0);
        Drawing.TextAlignment alignment =
            otherStarts > otherEnds
                ? Drawing.TextAlignment.LEFT
                : otherEnds > otherStarts
                    ? Drawing.TextAlignment.RIGHT
                    : Drawing.TextAlignment.CENTRE;
        Drawing.Label positioned =
            new Drawing.Label(
                label,
                (start + 2) * cellWidth,
                (y + 2.5) * cellHeight,
                colour,
                cellWidth,
                drawing.font,
                alignment);
        if (intersectsArrow(positioned.bounds(), arrowCells)) positioned.colour = Color.WHITE;
        drawing.labels.add(positioned);
      }
    }
  }

  private boolean intersectsArrow(Rectangle2D bounds, Drawing.Figure[][] arrows) {
    int left = Math.max(0, (int) Math.floor(bounds.getMinX() / cellWidth) - 2);
    int right = Math.min(width - 1, (int) Math.floor(bounds.getMaxX() / cellWidth) - 2);
    int top = Math.max(0, (int) Math.floor(bounds.getMinY() / cellHeight) - 2);
    int bottom = Math.min(height - 1, (int) Math.floor(bounds.getMaxY() / cellHeight) - 2);
    for (int y = top; y <= bottom; y++)
      for (int x = left; x <= right; x++)
        if (arrows[y][x] != null && arrows[y][x].path.intersects(bounds)) return true;
    return false;
  }

  private int[][] textGroups() {
    int[][] groups = new int[height][width];
    for (int[] row : groups) Arrays.fill(row, -1);
    ArrayDeque<Integer> pending = new ArrayDeque<>();
    int group = 0;
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        if (groups[y][x] != -1 || !textCell(x, y)) continue;
        groups[y][x] = group;
        pending.add(y * width + x);
        while (!pending.isEmpty()) {
          int cell = pending.removeFirst();
          int px = cell % width, py = cell / width;
          for (int d = 0; d < 4; d++) {
            int nx = px + DX[d], ny = py + DY[d];
            if (nx >= 0
                && nx < width
                && ny >= 0
                && ny < height
                && groups[ny][nx] == -1
                && textCell(nx, ny)) {
              groups[ny][nx] = group;
              pending.addLast(ny * width + nx);
            }
          }
        }
        group++;
      }
    return groups;
  }

  private boolean textCell(int x, int y) {
    return at(x, y) != ' ' || (at(x - 1, y) != ' ' && at(x + 1, y) != ' ');
  }

  private boolean textStartsAt(int x, int y) {
    return at(x, y) != ' ' && at(x - 1, y) == ' ';
  }

  private boolean textEndsAt(int x, int y) {
    return at(x, y) != ' ' && at(x + 1, y) == ' ';
  }

  private void connectors(int x, int y, Set<Long> boundary, Set<Long> drawn, Drawing drawing) {
    for (int direction = 0; direction < 4; direction++) {
      if ((links[y][x] & (1 << direction)) == 0) continue;
      int nx = x + DX[direction], ny = y + DY[direction];
      if (drawn.contains(edge(x, y, nx, ny))) continue;
      List<Point2D.Double> points = new ArrayList<>();
      points.add(new Point2D.Double(x, y));
      int px = x, py = y, d = direction;
      boolean dashed = at(px, py) == '=' || at(px, py) == ':';
      do {
        nx = px + DX[d];
        ny = py + DY[d];
        if (!drawn.add(edge(px, py, nx, ny))) break;
        points.add(new Point2D.Double(nx, ny));
        dashed |= at(nx, ny) == '=' || at(nx, ny) == ':';
        // An arrowhead terminates each shaft independently of the arrow's direction.
        if (Integer.bitCount(links[ny][nx]) != 2 || "<>^vV".indexOf(at(nx, ny)) >= 0) break;
        int remaining = links[ny][nx] & ~(1 << ((d + 2) % 4));
        px = nx;
        py = ny;
        d = Integer.numberOfTrailingZeros(remaining);
      } while (true);
      if (Integer.bitCount(links[y][x]) == 1 && Integer.bitCount(links[ny][nx]) == 1) {
        // The reference starts dashes at the first endpoint in its padded grid hash order.
        int capacity = 16;
        while (points.size() > capacity * .75) capacity *= 2;
        int first = (((x + 2) << 16) | (y + 2));
        int last = (((nx + 2) << 16) | (ny + 2));
        if (((last ^ (last >>> 16)) & (capacity - 1)) < ((first ^ (first >>> 16)) & (capacity - 1)))
          Collections.reverse(points);
      }
      Path2D.Double path = new Path2D.Double();
      Point2D.Double startPosition = connectorEndpoint(points, 0, boundary);
      path.moveTo(startPosition.x, startPosition.y);
      List<Point2D.Float> corners = new ArrayList<>();
      corners.add(new Point2D.Float((float) cx(points.get(0).x), (float) cy(points.get(0).y)));
      for (int i = 1; i < points.size() - 1; i++) {
        Point2D.Double before = points.get(i - 1), point = points.get(i), after = points.get(i + 1);
        if ((point.x - before.x) * (after.y - point.y)
            == (point.y - before.y) * (after.x - point.x)) continue;
        double pxCentre = cx(point.x), pyCentre = cy(point.y);
        corners.add(new Point2D.Float((float) pxCentre, (float) pyCentre));
        if (at((int) point.x, (int) point.y) == '/' || at((int) point.x, (int) point.y) == '\\') {
          path.lineTo(
              pxCentre + cellBoundaryOffset(before.x - point.x, cellWidth),
              pyCentre + cellBoundaryOffset(before.y - point.y, cellHeight));
          path.quadTo(
              pxCentre,
              pyCentre,
              pxCentre + cellBoundaryOffset(after.x - point.x, cellWidth),
              pyCentre + cellBoundaryOffset(after.y - point.y, cellHeight));
        } else {
          path.lineTo(pxCentre, pyCentre);
        }
      }
      Point2D.Double end = points.get(points.size() - 1);
      Point2D.Double endPosition = connectorEndpoint(points, points.size() - 1, boundary);
      path.lineTo(endPosition.x, endPosition.y);
      corners.add(new Point2D.Float((float) cx(end.x), (float) cy(end.y)));
      drawing.figures.add(
          new Drawing.Figure(
              path, null, dashed, Drawing.Figure.Kind.CONNECTOR, polygonArea(corners)));
    }
  }

  private Point2D.Double connectorEndpoint(
      List<Point2D.Double> points, int index, Set<Long> boundary) {
    Point2D.Double point = points.get(index);
    double x = cx(point.x), y = cy(point.y);
    char c = at((int) point.x, (int) point.y);
    if (points.size() == 2 && (c == '-' || c == '|')) {
      Point2D.Double other = points.get(1 - index);
      int ox = (int) other.x, oy = (int) other.y;
      boolean anchored = "<>^vV".indexOf(at(ox, oy)) >= 0;
      if (!anchored && Integer.bitCount(links[oy][ox]) == 3) {
        int closedEdges = 0;
        for (int d = 0; d < 4; d++)
          if ((links[oy][ox] & (1 << d)) != 0
              && boundary.contains(edge(ox, oy, ox + DX[d], oy + DY[d]))) closedEdges++;
        anchored = closedEdges == 2;
      }
      if (anchored) {
        if (c == '-') x = (point.x + 2) * cellWidth + (point.x > other.x ? cellWidth - 1 : 0);
        else y = (point.y + 2) * cellHeight + (point.y > other.y ? cellHeight - 1 : 0);
      }
    }
    return new Point2D.Double(x, y);
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
    List<Point2D.Float> renderCorners = new ArrayList<>();
    Collections.reverse(corners.subList(1, corners.size()));
    // Boundary-cell hash order fixes the first curve and the dash phase.
    if (corners.stream()
        .anyMatch(
            i ->
                at((int) p.get(i).x, (int) p.get(i).y) == '/'
                    || at((int) p.get(i).x, (int) p.get(i).y) == '\\')) {
      int capacity = 16;
      while (p.size() > capacity * .75) capacity *= 2;
      int seed = 0, firstBucket = Integer.MAX_VALUE;
      for (int i = 0; i < p.size(); i++) {
        Point2D.Double point = p.get(i);
        int hash = (((int) point.x + 2) << 16) | ((int) point.y + 2);
        int bucket = (hash ^ (hash >>> 16)) & (capacity - 1);
        Point2D.Double first = p.get(seed);
        if (bucket < firstBucket || bucket == firstBucket && point.y < first.y) {
          seed = i;
          firstBucket = bucket;
        }
      }
      int first = 0, distance = p.size();
      for (int i = 0; i < corners.size(); i++) {
        int candidate = (seed - corners.get(i) + p.size()) % p.size();
        if (candidate < distance) {
          first = i;
          distance = candidate;
        }
      }
      Collections.rotate(corners, -first);
    }
    for (int i = 0; i < corners.size(); i++) {
      Point2D.Double previous = p.get(corners.get((i + corners.size() - 1) % corners.size()));
      int pointIndex = corners.get(i);
      Point2D.Double point = p.get(pointIndex), next = p.get(corners.get((i + 1) % corners.size()));
      double x = cx(point.x), y = cy(point.y);
      if (options.separation()) {
        // Sharing any cell edge separates the full straight side.
        if (sharedSegment(previous, point, sharedEdges)) {
          x += Math.signum(point.y - previous.y) * 2 * options.scale();
          y -= Math.signum(point.x - previous.x) * 2 * options.scale();
        }
        if (sharedSegment(point, next, sharedEdges)) {
          x += Math.signum(next.y - point.y) * 2 * options.scale();
          y -= Math.signum(next.x - point.x) * 2 * options.scale();
        }
      }
      renderCorners.add(new Point2D.Float((float) x, (float) y));
      x = (int) x;
      y = (int) y;
      boolean rounded =
          at((int) point.x, (int) point.y) == '/' || at((int) point.x, (int) point.y) == '\\';
      double ix = x, iy = y;
      if (rounded) {
        ix =
            previous.x == point.x
                ? x
                : cx(point.x) + cellBoundaryOffset(previous.x - point.x, cellWidth);
        iy =
            previous.y == point.y
                ? y
                : cy(point.y) + cellBoundaryOffset(previous.y - point.y, cellHeight);
      }
      if (i == 0) path.moveTo(ix, iy);
      else path.lineTo(ix, iy);
      if (rounded)
        path.quadTo(
            x,
            y,
            next.x == point.x ? x : cx(point.x) + cellBoundaryOffset(next.x - point.x, cellWidth),
            next.y == point.y ? y : cy(point.y) + cellBoundaryOffset(next.y - point.y, cellHeight));
    }
    path.closePath();
    // Sort before integer path rounding; fractional shared edges can change equal-area ties.
    region.renderArea = polygonArea(renderCorners);
    Rectangle2D bounds = path.getBounds2D();
    double x = bounds.getX(), y = bounds.getY(), w = bounds.getWidth(), h = bounds.getHeight();
    double skew = cellWidth / 2.0;
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
        path.moveTo(x + skew, y);
        path.lineTo(x + w + skew, y);
        path.lineTo(x + w - skew, y + h);
        path.lineTo(x - skew, y + h);
        break;
      case "mo":
        path.moveTo(x - skew, y);
        path.lineTo(x + w + skew, y);
        path.lineTo(x + w - skew, y + h);
        path.lineTo(x + skew, y + h);
        break;
      case "tr":
        path.moveTo(x + skew, y);
        path.lineTo(x + w - skew, y);
        path.lineTo(x + w + skew, y + h);
        path.lineTo(x - skew, y + h);
        break;
      case "d":
        path.moveTo(x, y);
        path.lineTo(x + w, y);
        path.lineTo(x + w, y + h);
        path.quadTo(x + w / 2 + (int) (w / 6), y + h - cellHeight / 2, x + w / 2, y + h);
        path.quadTo(x + w / 2 - (int) (w / 6), y + h + cellHeight / 2, x, y + h);
        break;
      case "s":
        path.moveTo(x, y);
        path.curveTo(
            x + (int) (w / 6),
            y + cellHeight / 2,
            x + w - (int) (w / 6),
            y + cellHeight / 2,
            x + w,
            y);
        path.curveTo(
            x + w - (int) (w / 6), y - cellHeight / 2, x + (int) (w / 6), y - cellHeight / 2, x, y);
        path.moveTo(x, y);
        path.lineTo(x, y + h);
        path.curveTo(
            x + (int) (w / 6),
            y + h + Math.round(cellHeight * .7),
            x + w - (int) (w / 6),
            y + h + Math.round(cellHeight * .7),
            x + w,
            y + h);
        path.lineTo(x + w, y);
        return path;
      default:
        throw new IllegalStateException("Unknown shape tag");
    }
    path.closePath();
    return path;
  }

  private boolean sharedSegment(Point2D.Double start, Point2D.Double end, Set<Long> sharedEdges) {
    if (sharedEdges.isEmpty()) return false;
    int x = (int) start.x, y = (int) start.y;
    int dx = (int) Math.signum(end.x - start.x), dy = (int) Math.signum(end.y - start.y);
    while (x != end.x || y != end.y) {
      if (sharedEdges.contains(edge(x, y, x + dx, y + dy))) return true;
      x += dx;
      y += dy;
    }
    return false;
  }

  private static double polygonArea(List<Point2D.Float> corners) {
    double area = 0;
    for (int i = 0; i < corners.size(); i++) {
      Point2D.Float a = corners.get(i), b = corners.get((i + 1) % corners.size());
      area += a.x * b.y - b.x * a.y;
    }
    return Math.abs(area / 2);
  }

  private long edge(int x, int y, int nx, int ny) {
    int a = y * width + x, b = ny * width + nx;
    return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xffffffffL);
  }

  private static int cellBoundaryOffset(double direction, int size) {
    return direction > 0 ? size - size / 2 : direction < 0 ? -size / 2 : 0;
  }

  private double cx(double x) {
    return (x + 2) * cellWidth + cellWidth / 2;
  }

  private double cy(double y) {
    return (y + 2) * cellHeight + cellHeight / 2;
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
    double renderArea;

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
