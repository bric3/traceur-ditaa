/* SPDX-License-Identifier: MPL-2.0 */
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tabletest.junit.TableTest;

class CoverageBadgeTest {
  @TempDir
  Path directory;

  @TableTest({
    "missed | covered | expected",
    "11     | 1458    | 99.25   ",
    "1      | 2       | 66.67   ",
    "0      | 10      | 100.00  ",
    "10     | 0       | 0.00    "
  })
  void badgeUsesOnlyTheReportLineCounter(long missed, long covered, String expected)
      throws Exception {
    Path report = directory.resolve("jacoco.xml");
    Files.writeString(
        report,
        "<!DOCTYPE report PUBLIC \"-//JACOCO//DTD Report 1.1//EN\" \"report.dtd\">"
        + "<report><package><counter type='LINE' missed='900' covered='1'/></package>"
        + "<counter type='BRANCH' missed='1' covered='1'/>"
        + "<counter type='LINE' missed='"
        + missed
        + "' covered='"
        + covered
        + "'/></report>"
    );
    Path output = directory.resolve("badge/coverage.json");
    CoverageBadge.main(new String[] {report.toString(), output.toString()});
    assertEquals(
        "{\"schemaVersion\":1,\"label\":\"line coverage\",\"message\":\""
        + expected
        + "%\",\"color\":\"brightgreen\"}\n",
        Files.readString(output)
    );
  }

  @TableTest({
    "missed | covered",
    "0      | 0      ",
    "-1     | 10     ",
    "1      | -10    "
  })
  void emptyOrInvalidCountersAreRejected(long missed, long covered) throws Exception {
    Path report = directory.resolve("jacoco.xml");
    Files.writeString(
        report,
        "<report><counter type='LINE' missed='" + missed + "' covered='" + covered + "'/></report>"
    );
    Path output = directory.resolve("coverage.json");
    assertThrows(IllegalArgumentException.class, () -> CoverageBadge.main(
        new String[] {report.toString(), output.toString()}
    ));
    assertFalse(Files.exists(output));
  }

  @Test
  void aNestedCounterCannotReplaceTheReportTotal() throws Exception {
    Path report = directory.resolve("jacoco.xml");
    Files.writeString(
        report,
        "<report><package><counter type='LINE' missed='0' covered='10'/></package></report>"
    );
    assertThrows(IllegalArgumentException.class, () -> CoverageBadge.main(
        new String[] {report.toString(), directory.resolve("coverage.json").toString()}
    ));
  }
}
