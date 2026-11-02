// SPDX-License-Identifier: MPL-2.0
import com.diffplug.gradle.spotless.SpotlessExtension
import dev.yumi.gradle.licenser.YumiLicenserGradleExtension
import dev.yumi.gradle.licenser.api.rule.HeaderRule
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.diffplug.spotless")
    id("dev.yumi.gradle.licenser")
}

val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")
val ownedSources = fileTree(layout.projectDirectory) {
    include("src/**/*.java", "tools/*.java", "build-logic/src/**/*.kt", "**/*.gradle.kts")
    exclude("**/build/**", "**/.gradle/**")
}

configure<SpotlessExtension> {
    java {
        target(ownedSources.matching { include("**/*.java") })
        princeOfSpace(versions.findVersion("prince-of-space").get().requiredVersion).apply {
            indentSize(2)
            lineLength(100)
            javaLanguageLevel(25)
        }
        // Keep intentional NUL test values readable in source diffs.
        replace("escape NUL", "\u0000", "\\u0000")
        tableTestFormatter(versions.findVersion("tabletest-formatter").get().requiredVersion)
    }
    kotlin {
        target(ownedSources.matching { include("**/*.kt") })
        tableTestFormatter(versions.findVersion("tabletest-formatter").get().requiredVersion)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target(ownedSources.matching { include("**/*.gradle.kts") })
        trimTrailingWhitespace()
        endWithNewline()
    }
}

configure<YumiLicenserGradleExtension> {
    projectCreationYear = 2025
    excludeSourceSet("main")
    excludeSourceSet("test")
    excludeSourceSet("tools")
    rule(HeaderRule.parse("MPL-2.0", listOf("SPDX-License-Identifier: MPL-2.0")))
    headerCommentManager.register(SpdxHeaderComment(), "java", "kt", "kts")
    registerTasks("owned", ownedSources)
}
