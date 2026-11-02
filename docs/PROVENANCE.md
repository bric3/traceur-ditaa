# Implementation provenance

## Implementation sources

The renderer was implemented independently from documentation, public binary
APIs and black-box output comparisons. GPL/LGPL implementation sources and
decompiled code were excluded. This record has not had an independent legal audit.

| Reference                                                                                                                     | Use                                                                                |
| ----------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| [PlantUML ditaa documentation](https://plantuml.com/ditaa)                                                                    | Rendering behaviour                                                                |
| [ditaa usage documentation](https://github.com/stathissideris/ditaa/blob/master/README.md)                                    | Syntax and options                                                                 |
| Project-authored probes                                                                                                       | Dimensions, scaling, paths, colours, fonts, shadows and JDK drawing-call arguments |
| [jardiff version mapping](https://github.com/bric3/jardiff/blob/0c538e90a0c48571ce84ecf58d819898e5e9f1f4/settings.gradle.kts) | MPL-2.0 build version mapping                                                      |

Reference JARs run in separate processes. Production dependencies and release
artifacts exclude them.

## Fixture licences

| Material                                                     | Licence and record                                                                       |
| ------------------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `src/test/resources/corpus/`                                 | MPL-2.0                                                                                  |
| Project-authored compatibility fixtures and generated images | MPL-2.0                                                                                  |
| Seven `blog-*` fixtures and generated images                 | CC-BY-SA-4.0; [sources and notices](../src/test/resources/compatibility/BLOG-SOURCES.md) |

The blog diagrams were imported at the author's request.
`GenerateReferenceFixtures.java` records reference JAR checksums and JDK versions.

## External corpus

- The versioned manifest stores metadata only.
- Third-party inputs stay in an external cache.
- Comparison copies and images stay under ignored `build/`.
- `EXTERNAL_ONLY_CHECK_UPSTREAM_TERMS` marks unresolved redistribution rights.

Before committing or distributing an imported input or its output, establish its
licence and retain required notices. Generated images can inherit input restrictions;
see [PlantUML's output policy](https://plantuml.com/faq).
Public availability alone does not establish redistribution permission.
