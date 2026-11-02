/* SPDX-License-Identifier: MPL-2.0 */
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusComparisonTest {
  @TempDir
  Path directory;

  @Test
  void oracleAdapterRejectsMissingArgumentsBeforeLoadingTheReference() {
    assertThrows(IllegalArgumentException.class, () -> OraclePngProcess.main(new String[0]));
  }

  @Test
  void countsOneChangedPixelAndDetectsDimensionDifferences() throws Exception {
    var expected = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    var actual = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    assertEquals(0, CorpusComparison.compare(expected, actual, directory));
    actual.setRGB(1, 1, 0x01000000);
    assertEquals(1, CorpusComparison.compare(expected, actual, directory));
    var smaller = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    assertEquals(3, CorpusComparison.compare(expected, smaller, directory));
  }
}
