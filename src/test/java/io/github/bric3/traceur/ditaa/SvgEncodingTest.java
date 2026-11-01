/* SPDX-License-Identifier: MPL-2.0 */
package io.github.bric3.traceur.ditaa;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class SvgEncodingTest {
  @Test
  void dashedStorageHasTwoUnfilledDashedStrokes() throws Exception {
    var output = new ByteArrayOutputStream();
    new DitaaRenderer()
        .render(
            "+=======+\n: {s}   :\n+=======+",
            new RenderOptions(1, false, true),
            OutputFormat.SVG,
            output);
    var paths = parse(output.toByteArray()).getElementsByTagNameNS("*", "path");
    assertEquals(2, paths.getLength());
    var underlay = (Element) paths.item(0);
    var outline = (Element) paths.item(1);
    assertEquals("white", underlay.getAttribute("stroke"));
    assertEquals("none", underlay.getAttribute("fill"));
    assertEquals("none", outline.getAttribute("fill"));
    assertEquals("5 5", underlay.getAttribute("stroke-dasharray"));
    assertEquals("5 5", outline.getAttribute("stroke-dasharray"));
    assertEquals("butt", underlay.getAttribute("stroke-linecap"));
    assertEquals("butt", outline.getAttribute("stroke-linecap"));
    assertEquals(underlay.getAttribute("d"), outline.getAttribute("d"));
  }

  static Document parse(byte[] bytes) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }

  @Test
  void textOutsideAnArrowTriangleKeepsBlackForeground() throws Exception {
    var output = new ByteArrayOutputStream();
    new DitaaRenderer()
        .render(" i\n >", new RenderOptions(1, false, true), OutputFormat.SVG, output);
    Element text =
        (Element) parse(output.toByteArray()).getElementsByTagNameNS("*", "text").item(0);
    assertEquals("#000000", text.getAttribute("fill"));
  }

  @Test
  void textWhoseLogicalBoundsIntersectAnArrowUsesWhiteForeground() throws Exception {
    var output = new ByteArrayOutputStream();
    new DitaaRenderer()
        .render("^\nText", new RenderOptions(1, false, true), OutputFormat.SVG, output);
    Element text =
        (Element) parse(output.toByteArray()).getElementsByTagNameNS("*", "text").item(0);
    assertEquals("Text", text.getTextContent());
    assertEquals("#ffffff", text.getAttribute("fill"));
  }

  @Test
  void textOutsideAnArrowsLogicalBoundsKeepsBlackForeground() throws Exception {
    var output = new ByteArrayOutputStream();
    new DitaaRenderer()
        .render("^\n\nText", new RenderOptions(1, false, true), OutputFormat.SVG, output);
    Element text =
        (Element) parse(output.toByteArray()).getElementsByTagNameNS("*", "text").item(0);
    assertEquals("#000000", text.getAttribute("fill"));
  }

  @Test
  void roundedContourStartsAtTheFirstBoundaryCorner() throws Exception {
    var output = new ByteArrayOutputStream();
    new DitaaRenderer()
        .render(
            "    /--\\\n    |  |\n    \\--/",
            new RenderOptions(1, false, true),
            OutputFormat.SVG,
            output);
    Element path =
        (Element) parse(output.toByteArray()).getElementsByTagNameNS("*", "path").item(0);
    assertEquals(
        "M65 56Q65 63 70 63L90 63Q95 63 95 56L95 42Q95 35 90 35L70 35Q65 35 65 42Z",
        path.getAttribute("d"));
  }

  @ParameterizedTest
  @CsvSource({"0,0,30,35", "4,0,65,56", "0,3,55,84", "4,3,65,98", "19,0,215,56"})
  void roundedContourStartsFollowPaddedBoundaryCells(int column, int row, int x, int y)
      throws Exception {
    String indent = " ".repeat(column);
    String text = "\n".repeat(row) + indent + "/--\\\n" + indent + "|  |\n" + indent + "\\--/";
    var output = new ByteArrayOutputStream();
    new DitaaRenderer().render(text, new RenderOptions(1, false, true), OutputFormat.SVG, output);
    Element path =
        (Element) parse(output.toByteArray()).getElementsByTagNameNS("*", "path").item(0);
    assertTrue(path.getAttribute("d").startsWith("M" + x + " " + y + "Q"), path.getAttribute("d"));
  }

  @Test
  void textIsUtf8AndXmlEscapingPreservesEveryCharacter() throws Exception {
    String text = "café 日本語 😀 <A & B> \"quoted\" 'apostrophe'";
    var drawing = new Drawing(400, 80, new RenderOptions(1, false, true));
    drawing.labels.add(
        new Drawing.Label(text, 20, 35, Color.BLACK, 10, drawing.font, Drawing.TextAlignment.LEFT));
    var output = new ByteArrayOutputStream();
    SvgWriter.write(drawing, output);
    Document document = parse(output.toByteArray());
    assertEquals("http://www.w3.org/2000/svg", document.getDocumentElement().getNamespaceURI());
    assertEquals(text, document.getElementsByTagNameNS("*", "text").item(0).getTextContent());
    assertEquals(0, document.getElementsByTagNameNS("*", "image").getLength());
  }

  @Test
  void pathsPreserveLinesQuadraticCurvesCubicCurvesAndClosure() throws Exception {
    var drawing = new Drawing(120, 80, new RenderOptions(1, false, true));
    var path = new Path2D.Double();
    path.moveTo(1.25, 2.5);
    path.lineTo(3.5, 4.75);
    path.quadTo(5.25, 6.5, 7.75, 8.25);
    path.curveTo(9.5, 10.25, 11.75, 12.5, 13.25, 14.75);
    path.closePath();
    drawing.figures.add(
        new Drawing.Figure(path, new Color(0x000abc), false, Drawing.Figure.Kind.REGION, 0));
    var output = new ByteArrayOutputStream();
    SvgWriter.write(drawing, output);
    Element rendered =
        (Element) parse(output.toByteArray()).getElementsByTagNameNS("*", "path").item(0);
    assertEquals(
        "M1.25 2.5L3.5 4.75Q5.25 6.5 7.75 8.25C9.5 10.25 11.75 12.5 13.25 14.75Z",
        rendered.getAttribute("d"));
    assertEquals("#000abc", rendered.getAttribute("fill"));
  }

  @ParameterizedTest
  @CsvSource({
    "RED,ee3322",
    "BLU,5555bb",
    "GRE,99dd99",
    "PNK,ffaaaa",
    "BLK,000000",
    "YEL,ffff33",
    "0AF,00aaff",
    "F0A,ff00aa"
  })
  void namedAndHexadecimalColoursReachTheEncodedShape(String code, String expected)
      throws Exception {
    var output = new ByteArrayOutputStream();
    String text = "+---------+\n| c" + code + "    |\n|         |\n+---------+";
    new DitaaRenderer().render(text, new RenderOptions(1, false, true), OutputFormat.SVG, output);
    Document document = parse(output.toByteArray());
    assertEquals(
        "#" + expected,
        ((Element) document.getElementsByTagNameNS("*", "path").item(0)).getAttribute("fill"));
    assertEquals(0, document.getElementsByTagNameNS("*", "text").getLength());
  }

  @ParameterizedTest
  @CsvSource({
    "000,ffffff",
    "BBB,ffffff",
    "CCC,000000",
    "B00,ffffff",
    "C00,000000",
    "0B0,ffffff",
    "0C0,000000",
    "00B,ffffff",
    "00C,000000",
    "RED,000000",
    "BLU,ffffff",
    "FFF,000000"
  })
  void textUsesTheReferenceForegroundForShapeColours(String background, String foreground)
      throws Exception {
    var output = new ByteArrayOutputStream();
    new DitaaRenderer()
        .render(
            "+---------+\n| c" + background + "    |\n|    X    |\n+---------+",
            new RenderOptions(1, false, true),
            OutputFormat.SVG,
            output);
    Element text =
        (Element) parse(output.toByteArray()).getElementsByTagNameNS("*", "text").item(0);
    assertEquals("X ", text.getTextContent());
    assertEquals("#" + foreground, text.getAttribute("fill"));
  }
}
