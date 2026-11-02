# Contributing

## Build and checks

Gradle runs on Java 25. Install Java 11 as well: `build` runs the renderer tests
on both JDKs and compiles the library with `--release 11`. Use a Git checkout
with tags so the build can calculate its version. Set
`org.gradle.java.installations.paths` if Gradle cannot discover the toolchains.

```sh
./gradlew build
```

The build checks formatting, MPL-2.0 headers and at least 99% line and 97% branch
coverage, without exclusions. Library, source and Javadoc JARs go to `build/libs/`.

Spotless applies Prince of Space followed by TableTest Formatter. Yumi Licenser
owns the licence headers. Tool versions are centralised in the
[version catalog](gradle/libs.versions.toml).

```sh
./gradlew spotlessApply
./gradlew applyLicenses
```

Prefer TableTest for tabular cases, with argument converters where useful.
Use JUnit 5 constructs for generated matrices, concurrency and I/O checks.
Preserve diagram whitespace and assert observable rendering behaviour.

## Rendering verification

Normal SVG fixture tests render both reference and actual vectors in memory
with JSVG, then compare every pixel. The independent corpus check uses
`rsvg-convert`; CI and release builds require every comparison to pass before
publishing. Fixture generation and corpus tools also require Java 25 and JBang.

Use documentation, public binary APIs and rendered output as references.
Keep upstream implementation sources and decompiled code excluded.

- [Rendering, fixtures and corpus commands](docs/COMPATIBILITY.md)
- [Correctness, coverage and performance measurements](docs/VALIDATION.md)
- [Implementation provenance and fixture licences](docs/PROVENANCE.md)

## Versioning

Git tags supply release versions. A clean `v0.0.1` checkout builds as `0.0.1`;
later commits build as `0.0.2-SNAPSHOT`. Between `v0.0.2-rc.1` and `v0.0.2`,
development builds remain `0.0.2-SNAPSHOT`. Use `-Psnapshot=true` to force a
development snapshot at a release tag.

```sh
./gradlew printSemver
```

The calculated version is written to `build/semver/version.txt` and used in
the POM, JAR filenames and `Implementation-Version` manifest entry.

## CI and releases

[CI](.github/workflows/ci.yml) builds pull requests and pushes to `main`.
Successful `main` builds publish snapshots and refresh the newest draft release.
Create the draft with its intended version tag, such as `v0.1.0`. CI preserves
the title and release notes, updates its target commit, and packages the tested
classes with that version. The release action replaces the assets, and CI checks
the resulting draft target and three JAR uploads.
The tag must remain uncreated until publication; CI creates it only in its
private checkout for versioning. If there is no draft, the refresh is skipped.
CI also skips the draft refresh when `main` has advanced past the tested commit.
They also publish the combined Java 11/25 JaCoCo line coverage to `coverage.json`
on the `badges` branch. The README badge reads this data through Shields.io;
updates can take a few minutes to appear because of image caching.

To publish a release:

1. Open GitHub's **Releases > Draft a new release** and select a tested commit.
2. Enter a tag such as `v0.0.2-rc.1` for a prerelease or `v0.0.2` for a final release.
3. Set the prerelease checkbox to match the tag and publish the release.

The [release workflow](.github/workflows/release.yml) checks out that exact tag,
builds and tests, attaches the JARs, signs the artifacts and publishes to Maven
Central. Keep GitHub releases mutable so the workflow can attach the JARs.

## Maven Central configuration

The `traceur.central-publication` settings convention in `build-logic` configures
[Nmcp](https://gradleup.com/nmcp/) and Gradle signing. The library build owns
the Maven publication and POM metadata.

Maintainers need a [Central namespace](https://central.sonatype.com/publishing/namespaces)
covering `io.github.bric3.traceur`, with SNAPSHOTs enabled, and a
[Central user token](https://central.sonatype.com/usertoken). Configure these
GitHub Actions repository secrets:

| Secret                               | Value                                  |
| ------------------------------------ | -------------------------------------- |
| `MAVEN_CENTRAL_USERNAME`             | Username from the Central user token   |
| `MAVEN_CENTRAL_TOKEN`                | Password from the Central user token   |
| `ORG_GRADLE_PROJECT_SIGNINGKEY`      | ASCII-armoured private PGP signing key |
| `ORG_GRADLE_PROJECT_SIGNINGPASSWORD` | Passphrase for that key                |

Publish the corresponding public key to a
[Central-supported keyserver](https://central.sonatype.org/publish/requirements/gpg/#distributing-your-public-key).
Snapshots can publish without signing credentials. Prereleases and final
releases require signing; Central publishes them automatically after validation.

For local publishing, supply `mavenCentralUsername`, `mavenCentralPassword`,
`signingKey` and `signingPassword` in your user `gradle.properties`, or through
the corresponding `ORG_GRADLE_PROJECT_...` environment variables.

```sh
# Stage a development publication locally without uploading.
./gradlew nmcpZipAggregation -Psnapshot=true

# Publish a development snapshot.
./gradlew publishAggregationToCentralSnapshots -Psnapshot=true

# Publish an existing, clean release tag checked out at HEAD.
./gradlew publishAggregationToCentralPortal -PreleaseTag=v0.0.2
```
