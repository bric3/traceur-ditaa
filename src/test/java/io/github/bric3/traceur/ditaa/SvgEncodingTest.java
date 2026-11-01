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
  static Document parse(byte[] bytes) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }

  @Test
  void textIsUtf8AndXmlEscapingPreservesEveryCharacter() throws Exception {
    String text = "café 日本語 😀 <A & B> \"quoted\" 'apostrophe'";
    var drawing = new Drawing(400, 80, new RenderOptions(1, false, true));
    drawing.labels.add(new Drawing.Label(text, 20, 35, Color.BLACK, 10, drawing.font, true));
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
    drawing.figures.add(new Drawing.Figure(path, new Color(0x000abc), false, false, true));
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
  @CsvSource({"000,ffffff", "FFF,000000"})
  void textContrastsWithItsContainingShape(String background, String foreground) throws Exception {
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
