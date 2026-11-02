//JAVA 25
/* SPDX-License-Identifier: MPL-2.0 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Element;

/**
 * Generates Shields endpoint data from the combined JaCoCo line counter.
 */
public final class CoverageBadge {
  private CoverageBadge() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("JACOCO_XML OUTPUT_JSON");
    }
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    var document = factory.newDocumentBuilder().parse(Path.of(args[0]).toFile());
    var counter = (Element) XPathFactory
      .newInstance()
      .newXPath()
      .evaluate("/report/counter[@type='LINE']", document, XPathConstants.NODE);
    if (counter == null) {
      throw new IllegalArgumentException("Missing report line counter");
    }
    long missed = Long.parseLong(counter.getAttribute("missed"));
    long covered = Long.parseLong(counter.getAttribute("covered"));
    if (missed < 0 || covered < 0 || missed + covered == 0) {
      throw new IllegalArgumentException("Invalid report line counter");
    }
    double percentage = 100.0 * covered / (missed + covered);
    String json = String.format(
        Locale.ROOT,
        "{\"schemaVersion\":1,\"label\":\"line coverage\",\"message\":\"%.2f%%\",\"color\":"
        + "\"brightgreen\"}%n",
        percentage
    );
    Path output = Path.of(args[1]).toAbsolutePath();
    Files.createDirectories(output.getParent());
    Files.writeString(output, json);
    System.out.printf(Locale.ROOT, "Line coverage: %.2f%%%n", percentage);
  }
}
