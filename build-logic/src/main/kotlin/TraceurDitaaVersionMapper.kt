// SPDX-License-Identifier: MPL-2.0
import com.javiersc.gradle.version.GradleVersion
import com.javiersc.semver.project.gradle.plugin.VersionMapper

class TraceurDitaaVersionMapper(private val snapshot: Boolean) : VersionMapper {
    override fun map(version: GradleVersion): String {
        if (!snapshot && version.commits == 0 && version.metadata.isNullOrBlank()) {
            return version.copy(commits = null, hash = null).toString()
        }

        val patch = if (version.stage == null) version.patch + 1 else version.patch
        return "${version.major}.${version.minor}.$patch-SNAPSHOT"
    }

    companion object {
        fun releaseVersion(tag: String, prerelease: Boolean?): String {
            require(tag.startsWith("v")) { "Release tags must start with v: $tag" }
            val version = GradleVersion(tag.removePrefix("v"), GradleVersion.CheckMode.Significant)
            require(version.metadata == null && !version.stage?.name.equals("SNAPSHOT", ignoreCase = true)) {
                "Release tags must contain a release or prerelease version: $tag"
            }
            require(prerelease == null || prerelease == (version.stage != null)) {
                "The GitHub prerelease flag must match the release tag: $tag"
            }
            return version.toString()
        }
    }
}
