# Implementation provenance

The renderer was implemented independently from documentation and black-box
output comparisons. GPL/LGPL implementation sources were excluded. Reference
JARs execute in separate processes and are excluded from production dependencies
and release artifacts. This record has not undergone an independent legal audit.

Behavioural references are the [PlantUML ditaa documentation](https://plantuml.com/ditaa)
and [ditaa usage documentation](https://github.com/stathissideris/ditaa/blob/master/README.md).
Project-authored probes measured dimensions, scaling, paths, colours, fonts, and
shadows. Build version mapping follows MPL-2.0 code from
[jardiff](https://github.com/bric3/jardiff/blob/0c538e90a0c48571ce84ecf58d819898e5e9f1f4/settings.gradle.kts).

The fixtures in `src/test/resources/corpus` and the project-authored fixtures in
`src/test/resources/compatibility` use MPL-2.0, including generated reference images.
The seven `blog-*` fixtures were imported from Brice Dutheil's blog at the author's
request and retain CC-BY-SA-4.0. Their sources, licence and generated images are
documented in [BLOG-SOURCES.md](../src/test/resources/compatibility/BLOG-SOURCES.md).
`GenerateReferenceFixtures.java` records the reference checksums and JDK
versions. Public binary API probes and JDK drawing-call arguments supplement the
rendered output measurements; implementation sources and decompiled code remain excluded.

The versioned corpus manifest contains metadata only. Third-party inputs
stay in an external cache; comparison copies and images go under ignored `build/`.

`EXTERNAL_ONLY_CHECK_UPSTREAM_TERMS` records unresolved rights. Before committing
or distributing an imported input or its output, establish its licence and retain
the required notices. Generated images can carry restrictions from their inputs;
see [PlantUML's output policy](https://plantuml.com/faq). Public availability does
not establish redistribution permission.
