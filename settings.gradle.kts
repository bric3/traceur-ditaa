// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025 Brice Dutheil
import com.javiersc.gradle.version.GradleVersion
import com.javiersc.semver.project.gradle.plugin.VersionMapper

plugins {
    id("com.javiersc.semver") version "0.9.0"
}

// Keep tagged and dirty versions consistent with bric3/jardiff.
class TraceurDitaaVersionMapper(private val rootDirPath: String) : VersionMapper {
    override fun map(version: GradleVersion): String = when {
        version.commits == 0 && version.metadata.isNullOrBlank() ->
            version.copy(commits = null, hash = null).toString()

        version.hash == null && version.metadata == "DIRTY" -> {
            val process = ProcessBuilder("git", "-C", rootDirPath, "rev-parse", "--short=7", "HEAD")
                .redirectErrorStream(true)
                .start()
            val hash = process.inputStream.bufferedReader().use { it.readText() }.trim()
            check(process.waitFor() == 0) { "Cannot determine HEAD: $hash" }
            version.copy(hash = hash, metadata = "DIRTY").toString()
        }

        else -> version.toString()
    }
}

semver {
    mapVersion(TraceurDitaaVersionMapper(rootDir.absolutePath))
}

rootProject.name = "traceur-ditaa"
