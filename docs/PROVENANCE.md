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

The fixtures in `src/test/resources/corpus` were authored for Traceur Ditaa and use
MPL-2.0. The versioned corpus manifest contains metadata only. Third-party inputs
stay in an external cache; comparison copies and images go under ignored `build/`.

`EXTERNAL_ONLY_CHECK_UPSTREAM_TERMS` records unresolved rights. Before committing
or distributing an imported input or its output, establish its licence and retain
the required notices. Generated images can carry restrictions from their inputs;
see [PlantUML's output policy](https://plantuml.com/faq). Public availability does
not establish redistribution permission.
