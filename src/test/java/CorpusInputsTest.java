/* SPDX-License-Identifier: MPL-2.0 */
import static org.junit.jupiter.api.Assertions.*;
import io.github.bric3.traceur.ditaa.DiagramConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.converter.ConvertWith;
import org.tabletest.junit.TableTest;
import java.util.List;

class CorpusInputsTest {
  @TableTest({
    "format   | document                                               | expected         ",
    "plantuml | '@startditaa -S\\n  +--+\\n  é\\n@endditaa'            | '  +--+\\n  é\\n'",
    "markdown | '```ditaa\\n | |\\n```'                                | ' | |\\n'        ",
    "asciidoc | '[ditaa,box]\\n.Title\\n----\\n | |\\n----'            | ' | |\\n'        ",
    "org      | '#+BEGIN_SRC ditaa :file box.png\\n  +--+\\n#+END_SRC' | '  +--+\\n'      ",
    "arrows   | '```ditaa\\n--->\\n```'                                | '--->\\n'        ",
    "tilde    | '~~~ditaa\\n | |\\n~~~'                                | ' | |\\n'        "
  })
  void preservesDiagramWhitespace(
      String format,
      @ConvertWith(DiagramConverter.class) String document,
      @ConvertWith(DiagramConverter.class) String expected
  ) {
    assertEquals(List.of(expected), CorpusInputs.extract(document, false));
  }

  @Test
  void newFormatsDoNotRenumberExistingBlocks() {
    String source =
        "```ditaa\n--->\n```\n#+begin_src ditaa\n++\n#+end_src\n"
        + "[ditaa]\n----\n| adoc |\n----\n```ditaa\n| md |\n```\n"
        + "@startditaa\n| uml |\n@endditaa\n";
    assertEquals(
        List.of("| uml |\n", "| md |\n", "| adoc |\n", "++\n", "--->\n"),
        CorpusInputs.extract(source, false)
    );
  }

  @TableTest({
    "path                        | supported | raw  ",
    "'docs/é space.adoc'         | true      | false",
    "'docs/a.org'                | true      | false",
    "'diagram.ditaa'             | true      | true ",
    "'test-resources/text/a.txt' | true      | true ",
    "'docs/a.java'               | false     | false",
    "'../diagram.ditaa'          | false     | true ",
    "'/diagram.ditaa'            | false     | true "
  })
  void importsOnlyDiagramAndDocumentationPaths(String path, boolean supported, boolean raw) {
    assertEquals(supported, CorpusInputs.supported(path));
    assertEquals(raw, CorpusInputs.raw(path));
  }
}
