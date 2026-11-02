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
  private static final byte SOLID = 1;
  private static final byte DASHED = 2;
  private static final byte HIDDEN = 3;
  private static final Pattern MARKUP =
      Pattern.compile("c([0-9A-F]{3}|RED|BLU|GRE|PNK|BLK|YEL)|\\{(d|s|io|o|mo|c|tr)\\}");
  private static final int[] DX = {1, 0, -1, 0};
  private static final int[] DY = {0, 1, 0, -1};

  private final int width;
  private final int height;
  private final char[][] grid;
  private final int[][] links;
  private final byte[][] connectorStyles;
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
    connectorStyles = new byte[height][width];
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
        return "|:".indexOf(at(x, y - 1)) >= 0 ? arrowDirections(x, y) : 0;
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
    Map<Integer, Character> inlineLabels = new HashMap<>(), lineTypes = new HashMap<>();
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
    // Read the original neighbours before replacing single letters embedded in lines.
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        char c = at(x, y);
        if (!Character.isLetterOrDigit(c)) continue;
        boolean horizontal = "-=".indexOf(at(x - 1, y)) >= 0 && "-=".indexOf(at(x + 1, y)) >= 0;
        boolean vertical = "|:".indexOf(at(x, y - 1)) >= 0 && "|:".indexOf(at(x, y + 1)) >= 0;
        if (!horizontal && !vertical) continue;
        int cell = y * width + x;
        lineTypes.put(cell, horizontal && vertical ? '+' : vertical ? '|' : '-');
        if ("vV".indexOf(c) < 0 || "|:".indexOf(at(x, y - 1)) < 0) inlineLabels.put(cell, c);
      }
    lineTypes.forEach((cell, type) -> grid[cell / width][cell % width] = type);
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        for (int d = 0; d < 4; d++) {
          if ((directions(x, y) & (1 << d)) != 0
              && (directions(x + DX[d], y + DY[d]) & (1 << ((d + 2) % 4))) != 0) {
            links[y][x] |= 1 << d;
          }
        }
      }
    removePlusEnds();
    int[][] regionLinks = regionLinks();
    // A half-edge walk keeps the bounded regions separate when boxes share boundaries.
    boolean[][][] visited = new boolean[height][width][4];
    List<Region> regions = new ArrayList<>();
    Map<Integer, Region> enclosing = new HashMap<>();
    int[][] components = components(regionLinks);
    Set<Long> boundary = new HashSet<>();
    Set<Long> sharedEdges = new HashSet<>();
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        for (int d = 0; d < 4; d++) {
          if ((regionLinks[y][x] & (1 << d)) == 0 || visited[y][x][d]) continue;
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
              if ((regionLinks[ny][nx] & (1 << candidate)) != 0) {
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
          if (area != 0) {
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
            if (area < 0 && !bridges.isEmpty()) continue;
            if (area < 0) Collections.reverse(points);
            Region region =
                new Region(
                    points,
                    Math.abs(area),
                    dashed,
                    components[y][x],
                    faceEdges,
                    !bridges.isEmpty());
            if (area > 0) regions.add(region);
            else enclosing.put(region.component, region);
          }
        }
      }
    // An inward branch opens a compartment when its enclosing boundary remains closed.
    regions.removeIf(region -> region.branched && enclosing.containsKey(region.component));
    Map<Integer, Double> covered = new HashMap<>();
    Map<Integer, Integer> parts = new HashMap<>();
    for (Region region : regions) {
      covered.merge(region.component, region.area, Double::sum);
      parts.merge(region.component, 1, Integer::sum);
    }
    List<Region> contours = new ArrayList<>(regions);
    contours.addAll(enclosing.values());
    for (Region region : enclosing.values()) {
      // A contour that revisits a junction keeps its separate regions.
      boolean nested =
          parts.getOrDefault(region.component, 0) > 1
              && new HashSet<>(region.points).size() == region.points.size()
              && contours.stream()
                  .anyMatch(
                      parent ->
                          parent.component != region.component
                              && parent.gridPath.contains(region.points.get(0)));
      if (covered.getOrDefault(region.component, 0.0) < region.area || nested) regions.add(region);
    }
    for (Region region : regions)
      for (long edge : region.edges) {
        if (!boundary.add(edge)) sharedEdges.add(edge);
      }
    List<Region> shadowOrder = new ArrayList<>(regions);
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
      Shape shape = shape(region, sharedEdges);
      region.figure =
          new Drawing.Figure(
              shape,
              region.colour,
              region.dashed,
              region.tag.equals("s") ? Drawing.Figure.Kind.STORAGE : Drawing.Figure.Kind.REGION,
              region.renderArea);
    }
    regions.sort(Comparator.comparingDouble((Region r) -> r.renderArea).reversed());
    for (Region region : regions) drawing.figures.add(region.figure);
    for (Region region : shadowOrder) drawing.shadowFigures.add(region.figure);

    // Draw each remaining connector as a continuous path so dash phase spans cells.
    List<Integer> orderedConnectors = prepareConnectors(boundary);
    Set<Long> drawn = new HashSet<>(boundary);
    for (int seed : orderedConnectors) {
      int cell = seed / 4;
      connectors(cell % width, cell / width, boundary, drawn, drawing, 1 << (seed % 4));
    }
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
            .thenComparing(
                Comparator.comparingDouble(
                        (Drawing.Figure f) ->
                            f.kind == Drawing.Figure.Kind.STORAGE
                                ? f.path.getBounds2D().getCenterY()
                                : f.area)
                    .reversed()));

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) if (consumed[y][x]) grid[y][x] = ' ';
    }
    inlineLabels.forEach((cell, label) -> grid[cell / width][cell % width] = label);
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
        int otherEnds = textEndsAt(end, y) ? ends.getOrDefault(group | end, 0) - 1 : 0;
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
        Rectangle2D textBounds = positioned.bounds();
        if (intersectsArrow(textBounds, arrowCells)) positioned.colour = Color.WHITE;
        // Open connectors select text foreground after filled shapes and arrowheads.
        if (positioned.colour.equals(Color.WHITE))
          for (Drawing.Figure figure : drawing.figures)
            if (figure.kind == Drawing.Figure.Kind.CONNECTOR
                && figure.path.intersects(textBounds)) {
              positioned.colour = Color.BLACK;
              break;
            }
        drawing.labels.add(positioned);
      }
    }
  }

  private int[][] regionLinks() {
    // Arrowheads terminate shafts and cannot close a region.
    int[][] result = new int[height][];
    for (int y = 0; y < height; y++) result[y] = links[y].clone();
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        if ("<>^vV".indexOf(at(x, y)) < 0) continue;
        for (int d = 0; d < 4; d++)
          if ((links[y][x] & (1 << d)) != 0) result[y + DY[d]][x + DX[d]] &= ~(1 << ((d + 2) % 4));
        result[y][x] = 0;
      }
    return result;
  }

  private int[][] components(int[][] connections) {
    int[][] result = new int[height][width];
    int id = 0;
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        if (connections[y][x] == 0 || result[y][x] != 0) continue;
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(y * width + x);
        result[y][x] = ++id;
        while (!pending.isEmpty()) {
          int cell = pending.removeFirst(), cx = cell % width, cy = cell / width;
          for (int d = 0; d < 4; d++) {
            int nx = cx + DX[d], ny = cy + DY[d];
            if ((connections[cy][cx] & (1 << d)) == 0 || result[ny][nx] != 0) continue;
            result[ny][nx] = id;
            pending.add(ny * width + nx);
          }
        }
      }
    return result;
  }

  private List<Integer> prepareConnectors(Set<Long> boundary) {
    // Seeds encode a starting cell and direction as cell * 4 + direction.
    List<Integer> ordered = new ArrayList<>();
    Set<Long> visited = new HashSet<>(boundary);
    // Arrows and closed boundary cells separate the groups that share a dash style.
    boolean[][] anchors = new boolean[height][width];
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        anchors[y][x] = links[y][x] != 0 && "<>^vV".indexOf(at(x, y)) >= 0;
        for (int d = 0; d < 4; d++)
          if ((links[y][x] & (1 << d)) != 0 && boundary.contains(edge(x, y, x + DX[d], y + DY[d])))
            anchors[y][x] = true;
      }
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        if (anchors[y][x] || connectorStyles[y][x] != 0 || links[y][x] == 0) continue;
        List<Integer> cells = new ArrayList<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(y * width + x);
        connectorStyles[y][x] = SOLID;
        boolean dashed = false;
        while (!pending.isEmpty()) {
          int cell = pending.removeFirst();
          cells.add(cell);
          int cx = cell % width, cy = cell / width;
          dashed |= at(cx, cy) == '=' || at(cx, cy) == ':';
          for (int d = 0; d < 4; d++) {
            int nx = cx + DX[d], ny = cy + DY[d];
            if ((links[cy][cx] & (1 << d)) == 0 || anchors[ny][nx] || connectorStyles[ny][nx] != 0)
              continue;
            connectorStyles[ny][nx] = SOLID;
            pending.add(ny * width + nx);
          }
        }
        // A dashed group with one cell has no stroke in the reference.
        if (dashed)
          for (int cell : cells)
            connectorStyles[cell / width][cell % width] = cells.size() == 1 ? HIDDEN : DASHED;
        orderConnectors(cells, anchors, visited, ordered);
      }
    return ordered;
  }

  private void orderConnectors(
      List<Integer> cells, boolean[][] anchors, Set<Long> visited, List<Integer> ordered) {
    // Endpoint buckets determine the first stroke and its dash phase.
    int capacity = 16;
    while (cells.size() > capacity * .75) capacity *= 2;
    int root = -1, firstBucket = Integer.MAX_VALUE;
    for (int cell : cells) {
      int x = cell % width, y = cell / width, neighbours = 0;
      for (int d = 0; d < 4; d++)
        if ((links[y][x] & (1 << d)) != 0 && !anchors[y + DY[d]][x + DX[d]]) neighbours++;
      if (neighbours > 1) continue;
      int bucket = boundaryBucket(x, y, capacity);
      if (bucket < firstBucket || bucket == firstBucket && cell < root) {
        root = cell;
        firstBucket = bucket;
      }
    }
    if (root < 0) root = cells.get(0);
    int mask = 15;
    for (int d : connectorDirections(root)) {
      int x = root % width, y = root / width, nx = x + DX[d], ny = y + DY[d];
      if ((links[y][x] & (1 << d)) != 0
          && anchors[ny][nx]
          && !visited.contains(edge(x, y, nx, ny))) {
        root = ny * width + nx;
        mask = 1 << ((d + 2) % 4);
        break;
      }
    }
    ArrayDeque<ConnectorVisit> pending = new ArrayDeque<>();
    pending.push(new ConnectorVisit(root, -1, mask));
    while (!pending.isEmpty()) {
      ConnectorVisit visit = pending.peek();
      if (visit.next == 4) {
        // Draw the incoming path after visiting the branches at its far end.
        pending.pop();
        if (visit.incoming >= 0) ordered.add(visit.incoming);
        continue;
      }
      int d = visit.directions[visit.next++], x = visit.cell % width, y = visit.cell / width;
      if ((visit.mask & links[y][x] & (1 << d)) == 0
          || visited.contains(edge(x, y, x + DX[d], y + DY[d]))) continue;
      int seed = visit.cell * 4 + d;
      do {
        int nx = x + DX[d], ny = y + DY[d];
        if (!visited.add(edge(x, y, nx, ny))) break;
        x = nx;
        y = ny;
        if (anchors[y][x] || Integer.bitCount(links[y][x]) != 2) break;
        d = Integer.numberOfTrailingZeros(links[y][x] & ~(1 << ((d + 2) % 4)));
      } while (true);
      if (anchors[y][x]) ordered.add(seed);
      else pending.push(new ConnectorVisit(y * width + x, seed, 15));
    }
  }

  private Integer[] connectorDirections(int cell) {
    // Neighbour sets use 16 buckets and prefer vertical cells when buckets tie.
    Integer[] directions = {1, 3, 0, 2};
    Arrays.sort(
        directions,
        Comparator.comparingInt(
            d -> boundaryBucket(cell % width + DX[d], cell / width + DY[d], 16)));
    return directions;
  }

  private void removePlusEnds() {
    // Snapshot the endpoints so adjacent '+' cells retain their original degree.
    List<Integer> ends = new ArrayList<>();
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        if (at(x, y) == '+' && Integer.bitCount(links[y][x]) == 1) ends.add(y * width + x);
      }
    for (int cell : ends) {
      int x = cell % width, y = cell / width;
      consumed[y][x] = true;
      int connections = links[y][x];
      links[y][x] = 0;
      if (connections == 0) continue;
      int direction = Integer.numberOfTrailingZeros(connections);
      links[y + DY[direction]][x + DX[direction]] &= ~(1 << ((direction + 2) % 4));
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
    connectors(x, y, boundary, drawn, drawing, 15);
  }

  private void connectors(
      int x, int y, Set<Long> boundary, Set<Long> drawn, Drawing drawing, int mask) {
    for (int direction = 0; direction < 4; direction++) {
      if ((links[y][x] & mask & (1 << direction)) == 0) continue;
      int nx = x + DX[direction], ny = y + DY[direction];
      if (drawn.contains(edge(x, y, nx, ny))) continue;
      if (cornerBridge(x, y, nx, ny, boundary)) {
        drawn.add(edge(x, y, nx, ny));
        continue;
      }
      List<Point2D.Double> points = new ArrayList<>();
      points.add(new Point2D.Double(x, y));
      int px = x, py = y, d = direction;
      boolean dashed = connectorStyles[py][px] == DASHED;
      boolean hidden = connectorStyles[py][px] == HIDDEN;
      do {
        nx = px + DX[d];
        ny = py + DY[d];
        if (!drawn.add(edge(px, py, nx, ny))) break;
        points.add(new Point2D.Double(nx, ny));
        dashed |= connectorStyles[ny][nx] == DASHED;
        hidden |= connectorStyles[ny][nx] == HIDDEN;
        // An arrowhead terminates each shaft independently of the arrow's direction.
        if (Integer.bitCount(links[ny][nx]) != 2 || "<>^vV".indexOf(at(nx, ny)) >= 0) break;
        int remaining = links[ny][nx] & ~(1 << ((d + 2) % 4));
        px = nx;
        py = ny;
        d = Integer.numberOfTrailingZeros(remaining);
      } while (true);
      if (hidden) continue;
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
    // A straight arm extends through an adjacent open rounded corner.
    if ((c == '+' || c == '*') && Integer.bitCount(links[(int) point.y][(int) point.x]) > 2) {
      Point2D.Double adjacent = points.get(index == 0 ? 1 : index - 1);
      int dx = (int) (point.x - adjacent.x), dy = (int) (point.y - adjacent.y);
      int ox = (int) point.x + dx, oy = (int) point.y + dy;
      char opposite = at(ox, oy);
      if (opposite == '/' || opposite == '\\') {
        for (int d = 0; d < 4; d++)
          if (DX[d] == dx
              && DY[d] == dy
              && (links[(int) point.y][(int) point.x] & (1 << d)) != 0
              && !boundary.contains(edge((int) point.x, (int) point.y, ox, oy)))
            return new Point2D.Double(cx(ox), cy(oy));
      }
    }
    if (points.size() == 2 && (c == '-' || c == '|')) {
      Point2D.Double other = points.get(1 - index);
      int ox = (int) other.x, oy = (int) other.y;
      boolean anchored = "<>^vV".indexOf(at(ox, oy)) >= 0;
      if (!anchored) {
        int closedEdges = 0, connections = 0;
        for (int d = 0; d < 4; d++)
          if ((links[oy][ox] & (1 << d)) != 0) {
            int nx = ox + DX[d], ny = oy + DY[d];
            if (boundary.contains(edge(ox, oy, nx, ny))) closedEdges++;
            else if (cornerBridge(ox, oy, nx, ny, boundary)) continue;
            connections++;
          }
        anchored = closedEdges == 2 && connections == 3;
      }
      if (anchored) {
        if (c == '-') x = (point.x + 2) * cellWidth + (point.x > other.x ? cellWidth - 1 : 0);
        else y = (point.y + 2) * cellHeight + (point.y > other.y ? cellHeight - 1 : 0);
      }
    }
    return new Point2D.Double(x, y);
  }

  private boolean cornerBridge(int x, int y, int nx, int ny, Set<Long> boundary) {
    return at(x, y) == '+'
        && at(nx, ny) == '+'
        && closedCorner(x, y, boundary)
        && closedCorner(nx, ny, boundary);
  }

  private boolean closedCorner(int x, int y, Set<Long> boundary) {
    int directions = 0;
    for (int d = 0; d < 4; d++)
      if ((links[y][x] & (1 << d)) != 0 && boundary.contains(edge(x, y, x + DX[d], y + DY[d])))
        directions |= 1 << d;
    return Integer.bitCount(directions) == 2 && directions != 5 && directions != 10;
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
    int capacity = 16;
    while (p.size() > capacity * .75) capacity *= 2;
    int seed = 0, firstBucket = Integer.MAX_VALUE;
    for (int i = 0; i < p.size(); i++) {
      Point2D.Double point = p.get(i);
      int bucket = boundaryBucket(point, capacity);
      Point2D.Double first = p.get(seed);
      if (bucket < firstBucket || bucket == firstBucket && point.y < first.y) {
        seed = i;
        firstBucket = bucket;
      }
    }
    boolean clockwise =
        boundaryBucket(p.get((seed + 1) % p.size()), 16)
            < boundaryBucket(p.get((seed + p.size() - 1) % p.size()), 16);
    if (clockwise) Collections.reverse(corners.subList(1, corners.size()));
    int first = 0, distance = p.size();
    for (int i = 0; i < corners.size(); i++) {
      int candidate =
          ((clockwise ? corners.get(i) - seed : seed - corners.get(i)) + p.size()) % p.size();
      if (candidate < distance) {
        first = i;
        distance = candidate;
      }
    }
    Collections.rotate(corners, -first);
    for (int i = 0; i < corners.size(); i++) {
      Point2D.Double previous = p.get(corners.get((i + corners.size() - 1) % corners.size()));
      int pointIndex = corners.get(i);
      Point2D.Double point = p.get(pointIndex), next = p.get(corners.get((i + 1) % corners.size()));
      double x = cx(point.x), y = cy(point.y);
      if (options.separation()) {
        double offset = (clockwise ? -2 : 2) * options.scale();
        // Sharing any cell edge separates the full straight side.
        if (sharedSegment(previous, point, sharedEdges)) {
          x += Math.signum(point.y - previous.y) * offset;
          y -= Math.signum(point.x - previous.x) * offset;
        }
        if (sharedSegment(point, next, sharedEdges)) {
          x += Math.signum(next.y - point.y) * offset;
          y -= Math.signum(next.x - point.x) * offset;
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
        int wave = (int) (h / 8);
        path.moveTo(x, y);
        path.lineTo(x + w, y);
        path.lineTo(x + w, y + h);
        path.quadTo(x + w / 2 + (int) (w / 6), y + h - wave, x + w / 2, y + h);
        path.quadTo(x + w / 2 - (int) (w / 6), y + h + wave, x, y + h);
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

  private static int boundaryBucket(Point2D.Double point, int capacity) {
    return boundaryBucket((int) point.x, (int) point.y, capacity);
  }

  private static int boundaryBucket(int x, int y, int capacity) {
    int hash = ((x + 2) << 16) | (y + 2);
    return (hash ^ (hash >>> 16)) & (capacity - 1);
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

  private final class ConnectorVisit {
    final int cell;
    final int incoming;
    final int mask;
    final Integer[] directions;
    int next;

    ConnectorVisit(int cell, int incoming, int mask) {
      this.cell = cell;
      this.incoming = incoming;
      this.mask = mask;
      directions = connectorDirections(cell);
    }
  }

  private static final class Region {
    final List<Point2D.Double> points;
    final Path2D.Double gridPath = new Path2D.Double();
    final double area;
    final boolean dashed;
    final int component;
    final Set<Long> edges;
    final boolean branched;
    Color colour = Color.WHITE;
    String tag = "";
    Drawing.Figure figure;
    double renderArea;

    Region(
        List<Point2D.Double> points,
        double area,
        boolean dashed,
        int component,
        Set<Long> edges,
        boolean branched) {
      this.points = points;
      this.area = area;
      this.dashed = dashed;
      this.component = component;
      this.edges = edges;
      this.branched = branched;
      gridPath.moveTo(points.get(0).x, points.get(0).y);
      for (int i = 1; i < points.size(); i++) gridPath.lineTo(points.get(i).x, points.get(i).y);
      gridPath.closePath();
    }
  }
}
