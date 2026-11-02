// SPDX-License-Identifier: MPL-2.0
import com.javiersc.semver.settings.gradle.plugin.SemverSettingsExtension

val releaseTag = providers.gradleProperty("releaseTag").filter { it.isNotBlank() }.orNull

if (releaseTag == null) {
    apply(plugin = "com.javiersc.semver")
    extensions.configure<SemverSettingsExtension> {
        mapVersion(TraceurDitaaVersionMapper(providers.gradleProperty("snapshot").orNull.toBoolean()))
    }
} else {
    val releaseVersion = TraceurDitaaVersionMapper.releaseVersion(
        releaseTag,
        providers.gradleProperty("releasePrerelease").orNull?.toBooleanStrict(),
    )
    fun git(vararg arguments: String): String = providers.exec {
        commandLine("git", "-C", rootDir.absolutePath, *arguments)
    }.standardOutput.asText.get().trim()

    require(git("rev-parse", "HEAD") == git("rev-parse", "refs/tags/$releaseTag^{commit}")) {
        "Release builds must check out $releaseTag"
    }
    require(git("status", "--porcelain").isEmpty()) { "Release builds require a clean checkout" }

    // The triggering tag supplies the version when several tags point to the same commit.
    gradle.lifecycle.beforeProject {
        version = releaseVersion
    }
}
